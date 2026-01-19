// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.zombie.CodeFoldingZombieUtils;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static com.intellij.openapi.editor.impl.FoldingKeys.SELECT_REGION_ON_CARET_NEARBY;
import static com.intellij.openapi.editor.impl.FoldingKeys.ZOMBIE_REGION_KEY;

final class UpdateFoldRegionsOperation implements Runnable {
  enum ApplyDefaultStateMode { YES, EXCEPT_CARET_REGION, NO }

  private static final Logger LOG = Logger.getInstance(UpdateFoldRegionsOperation.class);
  static final Key<Boolean> CAN_BE_REMOVED_WHEN_COLLAPSED = Key.create("canBeRemovedWhenCollapsed");
  static final Key<Boolean> COLLAPSED_BY_DEFAULT = Key.create("collapsedByDefault");
  static final Key<Boolean> KEEP_EXPANDED_ON_FIRST_COLLAPSE_ALL = Key.create("keepExpandedOnFirstCollapseAll");
  static final Key<String> SIGNATURE = Key.create("signature");
  static final Key<Boolean> UPDATE_REGION = Key.create("update");
  static final String NO_SIGNATURE = "no signature";

  private static final Comparator<PsiElement> COMPARE_BY_OFFSET_REVERSED = (element, element1) -> {
    int startOffsetDiff = element1.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
    return startOffsetDiff == 0 ? element1.getTextRange().getEndOffset() - element.getTextRange().getEndOffset() : startOffsetDiff;
  };

  private final Project myProject;
  private final Editor myEditor;
  private final PsiFile myFile;
  private final @NotNull ApplyDefaultStateMode myApplyDefaultState;
  private final MultiMap<PsiElement, FoldingUpdate.RegionInfo> myElementsToFoldMap =
    new MultiMap<>(new TreeMap<>(COMPARE_BY_OFFSET_REVERSED)) {
      @Override
      protected @NotNull Collection<FoldingUpdate.RegionInfo> createCollection() {
        return new ArrayList<>();
      }
    };
  private final Set<FoldingUpdate.RegionInfo> myRegionInfos = new LinkedHashSet<>();
  private final MultiMap<FoldingGroup, FoldingUpdate.RegionInfo> myGroupedRegionInfos = new MultiMap<>();
  private final boolean myKeepCollapsedRegions;
  private final boolean myForInjected;

  UpdateFoldRegionsOperation(@NotNull Project project,
                             @NotNull Editor editor,
                             @NotNull PsiFile file,
                             @NotNull List<? extends FoldingUpdate.RegionInfo> elementsToFold,
                             @NotNull ApplyDefaultStateMode applyDefaultState,
                             boolean keepCollapsedRegions,
                             boolean forInjected) {
    myProject = project;
    myEditor = editor;
    myFile = file;
    myApplyDefaultState = applyDefaultState;
    myKeepCollapsedRegions = keepCollapsedRegions;
    myForInjected = forInjected;
    for (FoldingUpdate.RegionInfo regionInfo : elementsToFold) {
      myElementsToFoldMap.putValue(regionInfo.psiElement(), regionInfo);
      myRegionInfos.add(regionInfo);
      FoldingGroup group = regionInfo.descriptor().getGroup();
      if (group != null) {
        myGroupedRegionInfos.putValue(group, regionInfo);
      }
    }
  }

  @Override
  public void run() {
    EditorFoldingInfo info = EditorFoldingInfo.get(myEditor);
    FoldingModelEx foldingModel = (FoldingModelEx)myEditor.getFoldingModel();

    Map<TextRange, Boolean> rangeToExpandStatusMap = removeInvalidRegions(foldingModel, info);

    Set<FoldRegion> shouldExpand = new HashSet<>();
    Set<FoldingGroup> groupExpand = new HashSet<>();
    List<FoldRegion> newRegions = addNewRegions(foldingModel, info, rangeToExpandStatusMap, shouldExpand, groupExpand);
    if (CodeFoldingManagerImpl.isAsyncFoldingUpdater(myEditor)) {
      Map<TextRange, Boolean> postponedExpansionMap = CodeFoldingManagerImpl.getAsyncExpandStatusMap(myEditor);
      if (postponedExpansionMap != null) {
        postponedExpansionMap.putAll(rangeToExpandStatusMap);
      }
      else {
        CodeFoldingManagerImpl.setAsyncExpandStatusMap(myEditor, rangeToExpandStatusMap);
      }
    }
    applyExpandStatus(newRegions, shouldExpand, groupExpand);
    foldingModel.clearDocumentRangesModificationStatus();
    CodeFoldingZombieUtils.INSTANCE.postponeAndScheduleCleanupZombieRegions(myEditor);
  }

  private static void applyExpandStatus(@NotNull List<? extends FoldRegion> newRegions,
                                        @NotNull Set<? extends FoldRegion> shouldExpand,
                                        @NotNull Set<? extends FoldingGroup> groupExpand) {
    for (FoldRegion region : newRegions) {
      FoldingGroup group = region.getGroup();
      boolean expanded = group == null ? shouldExpand.contains(region) : groupExpand.contains(group);
      region.setExpanded(expanded);
    }
  }

  @RequiresEdt
  private @NotNull List<FoldRegion> addNewRegions(@NotNull FoldingModelEx foldingModel,
                                                  @NotNull EditorFoldingInfo editorFoldingInfo,
                                                  @NotNull @Unmodifiable Map<TextRange, Boolean> rangeToExpandStatusMap,
                                                  @NotNull Set<? super FoldRegion> shouldExpand,
                                                  @NotNull Set<? super FoldingGroup> groupExpand) {
    List<FoldRegion> newRegions = new ArrayList<>(myRegionInfos.size());
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    TextRange rangeToUnfoldOnNavigation = OpenFileDescriptor.getRangeToUnfoldOnNavigation(myEditor);
    int caretOffset = myEditor.getCaretModel().getOffset();
    for (FoldingUpdate.RegionInfo regionInfo : myRegionInfos) {
      ProgressManager.checkCanceled();
      FoldingDescriptor descriptor = regionInfo.descriptor();
      FoldingGroup group = descriptor.getGroup();
      TextRange range = descriptor.getRange();
      String placeholder = null;
      try {
        placeholder = descriptor.getPlaceholderText();
      }
      catch (IndexNotReadyException ignore) {
      }
      if (range.getEndOffset() > myEditor.getDocument().getTextLength()) {
        LOG.error(String.format("Invalid folding descriptor detected (%s). It ends beyond the document range (%d)",
                                descriptor, myEditor.getDocument().getTextLength()));
        continue;
      }

      FoldRegion region = createOrMergeWithZombie(foldingModel, range, placeholder == null ? "..." : placeholder, group, descriptor.isNonExpandable());
      if (region == null) continue;
      
      PsiElement psi;
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-326651, EA-831712")) {
        psi = descriptor.getElement().getPsi();
        if (psi == null || !psi.isValid() || !myFile.isValid()) {
          region.dispose();
          continue;
        }
      }

      region.setGutterMarkEnabledForSingleLine(descriptor.isGutterMarkEnabledForSingleLine());

      if (descriptor.canBeRemovedWhenCollapsed()) {
        region.putUserData(CAN_BE_REMOVED_WHEN_COLLAPSED, Boolean.TRUE);
      }
      CodeFoldingManagerImpl.markAsFrontendCreated(region);
      CodeFoldingManagerImpl.setCollapsedByDefault(region, regionInfo.collapsedByDefault());
      region.putUserData(KEEP_EXPANDED_ON_FIRST_COLLAPSE_ALL, regionInfo.keepExpandedOnFirstCollapseAll());
      region.putUserData(SIGNATURE, ObjectUtils.chooseNotNull(regionInfo.signature(), NO_SIGNATURE));

      editorFoldingInfo.addRegion(region, smartPointerManager.createSmartPsiElementPointer(psi));
      newRegions.add(region);

      if (descriptor.isNonExpandable()) {
        region.putUserData(SELECT_REGION_ON_CARET_NEARBY, Boolean.TRUE);
      }
      else {
        boolean expandStatus = shouldExpandNewRegion(range, rangeToExpandStatusMap, regionInfo.collapsedByDefault(), rangeToUnfoldOnNavigation, caretOffset);
        if (expandStatus) {
          if (group == null) {
            shouldExpand.add(region);
          }
          else {
            groupExpand.add(group);
          }
        }
      }
    }

    return newRegions;
  }

  @RequiresEdt
  private static @Nullable FoldRegion createOrMergeWithZombie(@NotNull FoldingModelEx foldingModel,
                                                              @NotNull TextRange range,
                                                              @NotNull String placeholder,
                                                              @Nullable FoldingGroup group,
                                                              boolean shouldNeverExpand) {
    FoldRegion region = null;

    FoldRegion zombieFoldRegion = foldingModel.getFoldRegion(range.getStartOffset(), range.getEndOffset());
    if (zombieFoldRegion != null && ZOMBIE_REGION_KEY.isIn(zombieFoldRegion)) {
      // check zombie for reuse to avoid blinking
      if (placeholder.equals(zombieFoldRegion.getPlaceholderText())
          && group == zombieFoldRegion.getGroup()
          && shouldNeverExpand == zombieFoldRegion.shouldNeverExpand()) {
        region = zombieFoldRegion;
        ZOMBIE_REGION_KEY.set(region, null); // bless a zombie for a new life
      }
      else {
        foldingModel.removeFoldRegion(zombieFoldRegion);
      }
    }

    if (region == null) {
      region = foldingModel.createFoldRegion(range.getStartOffset(), range.getEndOffset(), placeholder, group, shouldNeverExpand);
    }
    return region;
  } 

  static boolean caretInsideRange(int caretOffset, @NotNull TextRange range) {
    return range.contains(caretOffset) && range.getStartOffset() != caretOffset;
  }

  private boolean shouldExpandNewRegion(@NotNull TextRange range,
                                        @NotNull @Unmodifiable Map<TextRange, Boolean> rangeToExpandStatusMap,
                                        boolean collapsedByDefault,
                                        @NotNull TextRange rangeToUnfoldOnNavigation,
                                        int caretOffset) {
    if (myApplyDefaultState != ApplyDefaultStateMode.NO) {
      // Considering that this code is executed only on initial fold regions construction on editor opening.
      if (myApplyDefaultState == ApplyDefaultStateMode.EXCEPT_CARET_REGION) {
        if (rangeToUnfoldOnNavigation.intersects(range)) {
          return true;
        }
      }
      return !collapsedByDefault;
    }

    Boolean oldStatus = rangeToExpandStatusMap.get(range);
    return oldStatus == null || oldStatus.booleanValue() || caretInsideRange(caretOffset, range);
  }

  private @NotNull Map<TextRange, Boolean> removeInvalidRegions(@NotNull FoldingModelEx foldingModel, @NotNull EditorFoldingInfo info) {
    FoldRegion[] allFoldRegions = foldingModel.getAllFoldRegions();
    Map<TextRange, Boolean> rangeToExpandStatusMap = HashMap.newHashMap(allFoldRegions.length);
    List<FoldRegion> toRemove = new ArrayList<>();
    Ref<FoldingUpdate.RegionInfo> infoRef = Ref.create();
    Set<FoldingGroup> processedGroups = new HashSet<>();
    List<FoldingUpdate.RegionInfo> matchedInfos = new ArrayList<>();
    for (FoldRegion region : allFoldRegions) {
      FoldingGroup group = region.getGroup();
      if (group != null && !processedGroups.add(group)) continue;

      List<FoldRegion> regionsToProcess = group == null ? Collections.singletonList(region) : foldingModel.getGroupedRegions(group);
      matchedInfos.clear();
      boolean shouldRemove = false;
      boolean isLight = true;
      for (FoldRegion regionToProcess : regionsToProcess) {
        if (!regionToProcess.isValid() || shouldRemoveRegion(foldingModel, regionToProcess, info, rangeToExpandStatusMap, infoRef)) {
          shouldRemove = true;
        }
        isLight &= regionToProcess.getUserData(SIGNATURE) == null;
        FoldingUpdate.RegionInfo regionInfo = infoRef.get();
        matchedInfos.add(regionInfo);
      }
      if (!shouldRemove && group != null && !isLight) {
        FoldingGroup requestedGroup = null;
        for (FoldingUpdate.RegionInfo matchedInfo : matchedInfos) {
          if (matchedInfo == null) {
            shouldRemove = true;
            break;
          }
          FoldingGroup g = matchedInfo.descriptor().getGroup();
          if (g == null) {
            shouldRemove = true;
            break;
          }
          if (requestedGroup == null) {
            requestedGroup = g;
          }
          else if (!requestedGroup.equals(g)) {
            shouldRemove = true;
            break;
          }
        }
        if (myGroupedRegionInfos.get(requestedGroup).size() != matchedInfos.size()) {
          shouldRemove = true;
        }
      }
      if (shouldRemove) {
        for (FoldRegion r : regionsToProcess) {
          rangeToExpandStatusMap.putIfAbsent(r.getTextRange(), r.isExpanded());
        }
        toRemove.addAll(regionsToProcess);
      }
      else {
        for (FoldingUpdate.RegionInfo matchedInfo : matchedInfos) {
          if (matchedInfo != null) {
            myElementsToFoldMap.remove(matchedInfo.psiElement(), matchedInfo);
            myRegionInfos.remove(matchedInfo);
          }
        }
      }
    }

    for (FoldRegion region : toRemove) {
      foldingModel.removeFoldRegion(region);
      info.removeRegion(region);
    }
    return rangeToExpandStatusMap;
  }

  private boolean shouldRemoveRegion(@NotNull FoldingModelEx foldingModel,
                                     @NotNull FoldRegion region,
                                     @NotNull EditorFoldingInfo info,
                                     @NotNull Map<? super TextRange, ? super Boolean> rangeToExpandStatusMap,
                                     @NotNull Ref<? super FoldingUpdate.RegionInfo> matchingInfo) {
    matchingInfo.set(null);
    if (UPDATE_REGION.get(region) == Boolean.TRUE) {
      rangeToExpandStatusMap.put(region.getTextRange(), region.isExpanded());
      return true;
    }
    PsiElement element = info.getPsiElement(region);
    if (element != null) {
      PsiFile containingFile = element.getContainingFile();
      boolean isInjected = InjectedLanguageManager.getInstance(myProject).isInjectedFragment(containingFile);
      if (isInjected != myForInjected) {
        return false;
      }
    }
    boolean forceKeepRegion = myKeepCollapsedRegions && !region.isExpanded() && !regionOrGroupCanBeRemovedWhenCollapsed(foldingModel, region);
    Boolean storedCollapsedByDefault = CodeFoldingManagerImpl.getCollapsedByDefault(region);
    Collection<FoldingUpdate.RegionInfo> regionInfos;
    if (element != null && !(regionInfos = myElementsToFoldMap.get(element)).isEmpty()) {
      FoldingUpdate.RegionInfo[] array = regionInfos.toArray(new FoldingUpdate.RegionInfo[0]);
      for (FoldingUpdate.RegionInfo regionInfo : array) {
        FoldingDescriptor descriptor = regionInfo.descriptor();
        TextRange range = descriptor.getRange();
        if (TextRange.areSegmentsEqual(region, range)) {
          if (storedCollapsedByDefault != null && storedCollapsedByDefault != regionInfo.collapsedByDefault()) {
            rangeToExpandStatusMap.put(range, !regionInfo.collapsedByDefault());
            return true;
          }
          else if (!region.getPlaceholderText().equals(descriptor.getPlaceholderText()) || range.getLength() < 2) {
            return true;
          }
          else {
            matchingInfo.set(regionInfo);
            return false;
          }
        }
      }
      if (!forceKeepRegion) {
        for (FoldingUpdate.RegionInfo regionInfo : regionInfos) {
          rangeToExpandStatusMap.put(regionInfo.descriptor().getRange(), region.isExpanded());
        }
        return true;
      }
      return false;
    }
    // In the case of auto-created folding, we need to ensure that we really need to ensure that the region is safe to be removed.
    // Otherwise, backend-originated foldings could be removed without any new foldings (which is a case for frontend-rebuilt folding)
    if (CodeFoldingManagerImpl.isAutoCreated(region)) {
      // for auto-created foldings, CAN_BE_REMOVED_WHEN_COLLAPSED could be only inherited from the previously alive frontend folding
      // That previous folding is 99.9% a merge of the same foldings from backend and frontend (since they are for now placed in common modules).
      // However, if during the reparse + folding update, those foldings (e.g., new import added) should be extended, it, first, should be removed.
      // But due to the lack of that flag, it will not be removed and the state of the folding will be inconsistent on the back-/frontend.
      // That lead to IJPL-198085
      return !forceKeepRegion &&
             Boolean.TRUE.equals(region.getUserData(CAN_BE_REMOVED_WHEN_COLLAPSED));
    }
    else {
      return !forceKeepRegion &&
             !(region.getUserData(SIGNATURE) == null /* 'light' region */);
    }
  }

  private boolean regionOrGroupCanBeRemovedWhenCollapsed(@NotNull FoldingModelEx foldingModel, @NotNull FoldRegion region) {
    FoldingGroup group = region.getGroup();
    List<FoldRegion> affectedRegions = group != null
                                       ? foldingModel.getGroupedRegions(group)
                                       : Collections.singletonList(region);
    for (FoldRegion affectedRegion : affectedRegions) {
      if (regionCanBeRemovedWhenCollapsed(foldingModel, affectedRegion)) return true;
    }
    return false;
  }

  private boolean regionCanBeRemovedWhenCollapsed(@NotNull FoldingModelEx foldingModel, @NotNull FoldRegion region) {
    return Boolean.TRUE.equals(region.getUserData(CAN_BE_REMOVED_WHEN_COLLAPSED)) ||
           foldingModel.hasDocumentRegionChangedFor(region) ||
           !region.isValid() ||
           isRegionInCaretLine(region);
  }

  private boolean isRegionInCaretLine(@NotNull FoldRegion region) {
    int regionStartLine = myEditor.getDocument().getLineNumber(region.getStartOffset());
    int regionEndLine = myEditor.getDocument().getLineNumber(region.getEndOffset());
    int caretLine = myEditor.getCaretModel().getLogicalPosition().line;
    return caretLine >= regionStartLine && caretLine <= regionEndLine;
  }
}
