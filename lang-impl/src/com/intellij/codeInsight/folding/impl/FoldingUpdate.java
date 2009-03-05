package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import static com.intellij.util.containers.CollectionFactory.arrayList;
import static com.intellij.util.containers.CollectionFactory.newTroveMap;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class FoldingUpdate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingUpdate");

  private static final Key<Object> LAST_UPDATE_STAMP_KEY = Key.create("LAST_UPDATE_STAMP_KEY");
  private static final Comparator<PsiElement> COMPARE_BY_OFFSET = new Comparator<PsiElement>() {
      public int compare(PsiElement element, PsiElement element1) {
        int startOffsetDiff = element.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
        return startOffsetDiff == 0 ? element.getTextRange().getEndOffset() - element1.getTextRange().getEndOffset() : startOffsetDiff;
      }
    };

  private FoldingUpdate() {
  }

  @Nullable
  public static Runnable updateFoldRegions(final Editor editor, PsiElement file, boolean applyDefaultState) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = file.getProject();
    Document document = editor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));

    final long timeStamp = document.getModificationStamp();
    Object lastTimeStamp = editor.getUserData(LAST_UPDATE_STAMP_KEY);
    if (lastTimeStamp instanceof Long && ((Long)lastTimeStamp).longValue() == timeStamp && !applyDefaultState) return null;

    if (file instanceof PsiCompiledElement){
      file = ((PsiCompiledElement)file).getMirror();
    }

    final TreeMap<PsiElement, FoldingDescriptor> elementsToFoldMap = new TreeMap<PsiElement, FoldingDescriptor>(COMPARE_BY_OFFSET);
    final FileViewProvider viewProvider = ((PsiFile)file).getViewProvider();
    for (final Language language : viewProvider.getLanguages()) {
      final PsiFile psi = viewProvider.getPsi(language);
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if (psi != null && foldingBuilder != null) {
        final ASTNode node = psi.getNode();
        for (FoldingDescriptor descriptor : foldingBuilder.buildFoldRegions(node, document)) {
          elementsToFoldMap.put(SourceTreeToPsiMap.treeElementToPsi(descriptor.getElement()), descriptor);
        }
      }
    }


    final Runnable operation = new UpdateFoldRegionsOperation(editor, elementsToFoldMap, applyDefaultState);
    return new Runnable() {
      public void run() {
        editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
        editor.putUserData(LAST_UPDATE_STAMP_KEY, timeStamp);
      }
    };
  }

  private static class UpdateFoldRegionsOperation implements Runnable {
    private final Editor myEditor;
    private final boolean myApplyDefaultState;
    private final TreeMap<PsiElement, FoldingDescriptor> myElementsToFoldMap;

    private UpdateFoldRegionsOperation(Editor editor, TreeMap<PsiElement, FoldingDescriptor> elementsToFoldMap, boolean applyDefaultState) {
      myEditor = editor;
      myApplyDefaultState = applyDefaultState;
      myElementsToFoldMap = elementsToFoldMap;
    }

    public void run() {
      EditorFoldingInfo info = EditorFoldingInfo.get(myEditor);
      FoldingModelEx foldingModel = (FoldingModelEx)myEditor.getFoldingModel();
      HashMap<TextRange,Boolean> rangeToExpandStatusMap = new HashMap<TextRange, Boolean>();

      removeInvalidRegions(info, foldingModel, rangeToExpandStatusMap);

      Map<FoldRegion, Boolean> shouldExpand = newTroveMap();
      Map<FoldingGroup, Boolean> groupExpand = newTroveMap();
      List<FoldRegion> newRegions = addNewRegions(info, foldingModel, rangeToExpandStatusMap, shouldExpand, groupExpand);

      applyExpandStatus(newRegions, shouldExpand, groupExpand);
    }

    private static void applyExpandStatus(List<FoldRegion> newRegions, Map<FoldRegion, Boolean> shouldExpand, Map<FoldingGroup, Boolean> groupExpand) {
      for (final FoldRegion region : newRegions) {
        final Boolean expanded;
        final FoldingGroup group = region.getGroup();
        if (group != null) {
          expanded = groupExpand.get(group);
        } else {
          expanded = shouldExpand.get(region);
        }

        if (expanded != null) {
          region.setExpanded(expanded.booleanValue());
        }
      }
    }

    private List<FoldRegion> addNewRegions(EditorFoldingInfo info, FoldingModelEx foldingModel, HashMap<TextRange, Boolean> rangeToExpandStatusMap,
                                           Map<FoldRegion, Boolean> shouldExpand,
                                           Map<FoldingGroup, Boolean> groupExpand) {
      List<FoldRegion> newRegions = arrayList();

      for (final Map.Entry<PsiElement, FoldingDescriptor> entry : myElementsToFoldMap.entrySet()) {
        ProgressManager.getInstance().checkCanceled();
        PsiElement element = entry.getKey();
        final FoldingDescriptor descriptor = entry.getValue();
        TextRange range = descriptor.getRange();
        FoldRegion region = new FoldRegionImpl(myEditor, range.getStartOffset(), range.getEndOffset(), descriptor.getPlaceholderText(),
                                               descriptor.getGroup());
        if (!foldingModel.addFoldRegion(region)) continue;

        info.addRegion(region, element);
        newRegions.add(region);

        boolean expandStatus = shouldExpandNewRegion(element, range, rangeToExpandStatusMap);
        final FoldingGroup group = region.getGroup();
        if (group == null) {
          shouldExpand.put(region, expandStatus);
        } else {
          final Boolean alreadyExpanded = groupExpand.get(group);
          groupExpand.put(group, alreadyExpanded == null ? expandStatus : alreadyExpanded.booleanValue() || expandStatus);
        }
      }
      return newRegions;
    }

    private boolean shouldExpandNewRegion(PsiElement element, TextRange range, HashMap<TextRange, Boolean> rangeToExpandStatusMap) {
      boolean caretInside = FoldingUtil.caretInsideRange(myEditor, range);
      if (myApplyDefaultState) {
        return caretInside || !FoldingPolicy.isCollapseByDefault(element);
      }

      final Boolean oldStatus = rangeToExpandStatusMap.get(range);
      if (oldStatus != null) {
        return caretInside || oldStatus.booleanValue();
      }

      return true;
    }

    private void removeInvalidRegions(EditorFoldingInfo info, FoldingModelEx foldingModel, HashMap<TextRange, Boolean> rangeToExpandStatusMap) {
      List<FoldRegion> toRemove = arrayList();
      for (FoldRegion region : foldingModel.getAllFoldRegions()) {
        PsiElement element = info.getPsiElement(region);
        if (element != null && myElementsToFoldMap.containsKey(element)) {
          final FoldingDescriptor descriptor = myElementsToFoldMap.get(element);
          if (!region.isValid() ||
              region.getGroup() != null ||
              descriptor.getGroup() != null ||
              region.getStartOffset() != descriptor.getRange().getStartOffset() ||
              region.getEndOffset() != descriptor.getRange().getEndOffset() ||
              !region.getPlaceholderText().equals(descriptor.getPlaceholderText())) {
            rangeToExpandStatusMap.put(descriptor.getRange(), region.isExpanded());
            toRemove.add(region);
          }
          else {
            myElementsToFoldMap.remove(element);
          }
        }
        else {
          if (region.isValid() && info.isLightRegion(region)) {
            boolean isExpanded = region.isExpanded();
            rangeToExpandStatusMap.put(new TextRange(region.getStartOffset(), region.getEndOffset()),
                                       isExpanded ? Boolean.TRUE : Boolean.FALSE);
          }
          else {
            toRemove.add(region);
          }
        }
      }

      for (final FoldRegion region : toRemove) {
        foldingModel.removeFoldRegion(region);
        info.removeRegion(region);
      }
    }

  }
}
