/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.newTroveMap;

/**
 * @author cdr
 */
class UpdateFoldRegionsOperation implements Runnable {

  enum ApplyDefaultStateMode { YES, EXCEPT_CARET_REGION, NO }
  
  private static final Logger LOG = Logger.getInstance("#" + UpdateFoldRegionsOperation.class.getName());
  private static final Key<Boolean> CAN_BE_REMOVED_WHEN_COLLAPSED = Key.create("canBeRemovedWhenCollapsed"); 
  
  private final Project myProject;
  private final Editor myEditor;
  private final PsiFile myFile;
  private final ApplyDefaultStateMode myApplyDefaultState;
  private final FoldingUpdate.FoldingMap myElementsToFoldMap;
  private final boolean myKeepCollapsedRegions;
  private final boolean myForInjected;

  UpdateFoldRegionsOperation(@NotNull Project project,
                             @NotNull Editor editor,
                             @NotNull PsiFile file,
                             @NotNull FoldingUpdate.FoldingMap elementsToFoldMap,
                             ApplyDefaultStateMode applyDefaultState,
                             boolean keepCollapsedRegions,
                             boolean forInjected) {
    myProject = project;
    myEditor = editor;
    myFile = file;
    myApplyDefaultState = applyDefaultState;
    myElementsToFoldMap = elementsToFoldMap;
    myKeepCollapsedRegions = keepCollapsedRegions;
    myForInjected = forInjected;
  }

  @Override
  public void run() {
    EditorFoldingInfo info = EditorFoldingInfo.get(myEditor);
    FoldingModelEx foldingModel = (FoldingModelEx)myEditor.getFoldingModel();
    Map<TextRange,Boolean> rangeToExpandStatusMap = newTroveMap();

    // FoldingUpdate caches instances of our object, so they must be immutable.
    FoldingUpdate.FoldingMap elementsToFold = new FoldingUpdate.FoldingMap(myElementsToFoldMap); 

    removeInvalidRegions(info, foldingModel, elementsToFold, rangeToExpandStatusMap);

    Map<FoldRegion, Boolean> shouldExpand = newTroveMap();
    Map<FoldingGroup, Boolean> groupExpand = newTroveMap();
    List<FoldRegion> newRegions = addNewRegions(info, foldingModel, elementsToFold, rangeToExpandStatusMap, shouldExpand, groupExpand);

    applyExpandStatus(newRegions, shouldExpand, groupExpand);
    
    foldingModel.clearDocumentRangesModificationStatus();
  }

  private static void applyExpandStatus(@NotNull List<FoldRegion> newRegions,
                                        @NotNull Map<FoldRegion, Boolean> shouldExpand,
                                        @NotNull Map<FoldingGroup, Boolean> groupExpand) {
    for (final FoldRegion region : newRegions) {
      final FoldingGroup group = region.getGroup();
      final Boolean expanded = group == null ? shouldExpand.get(region) : groupExpand.get(group);

      if (expanded != null) {
        region.setExpanded(expanded.booleanValue());
      }
    }
  }

  private List<FoldRegion> addNewRegions(@NotNull EditorFoldingInfo info,
                                         @NotNull FoldingModelEx foldingModel,
                                         FoldingUpdate.FoldingMap elementsToFold, @NotNull Map<TextRange, Boolean> rangeToExpandStatusMap,
                                         @NotNull Map<FoldRegion, Boolean> shouldExpand,
                                         @NotNull Map<FoldingGroup, Boolean> groupExpand) {
    List<FoldRegion> newRegions = newArrayList();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    for (PsiElement element : elementsToFold.keySet()) {
      ProgressManager.checkCanceled();
      final Collection<FoldingDescriptor> descriptors = elementsToFold.get(element);
      for (FoldingDescriptor descriptor : descriptors) {
        FoldingGroup group = descriptor.getGroup();
        TextRange range = descriptor.getRange();
        String placeholder = descriptor.getPlaceholderText();
        if (range.getEndOffset() > myEditor.getDocument().getTextLength()) {
          LOG.error(String.format("Invalid folding descriptor detected (%s). It ends beyond the document range (%d)",
                                  descriptor, myEditor.getDocument().getTextLength()));
          continue;
        }
        FoldRegion region = foldingModel.createFoldRegion(range.getStartOffset(), range.getEndOffset(),
                                                          placeholder == null ? "..." : placeholder,
                                                          group,
                                                          descriptor.isNonExpandable());
        if (region == null) continue;

        PsiElement psi = descriptor.getElement().getPsi();

        if (psi == null || !psi.isValid() || !foldingModel.addFoldRegion(region) || !myFile.isValid()) {
          region.dispose();
          continue;
        }
        
        if (descriptor.canBeRemovedWhenCollapsed()) region.putUserData(CAN_BE_REMOVED_WHEN_COLLAPSED, Boolean.TRUE);

        info.addRegion(region, smartPointerManager.createSmartPsiElementPointer(psi));
        newRegions.add(region);

        boolean expandStatus = !descriptor.isNonExpandable() && shouldExpandNewRegion(element, range, rangeToExpandStatusMap);
        if (group == null) {
          shouldExpand.put(region, expandStatus);
        }
        else {
          final Boolean alreadyExpanded = groupExpand.get(group);
          groupExpand.put(group, alreadyExpanded == null ? expandStatus : alreadyExpanded.booleanValue() || expandStatus);
        }
      }
    }

    return newRegions;
  }

  private boolean shouldExpandNewRegion(PsiElement element, TextRange range, Map<TextRange, Boolean> rangeToExpandStatusMap) {
    if (myApplyDefaultState != ApplyDefaultStateMode.NO) {
      // Considering that this code is executed only on initial fold regions construction on editor opening.
      if (myApplyDefaultState == ApplyDefaultStateMode.EXCEPT_CARET_REGION) {
        TextRange lineRange = OpenFileDescriptor.getRangeToUnfoldOnNavigation(myEditor);
        if (lineRange.intersects(range)) {
          return true;
        }
      }
      return !FoldingPolicy.isCollapseByDefault(element);
    }

    final Boolean oldStatus = rangeToExpandStatusMap.get(range);
    return oldStatus == null || FoldingUtil.caretInsideRange(myEditor, range) || oldStatus.booleanValue();
  }

  private void removeInvalidRegions(@NotNull EditorFoldingInfo info,
                                    @NotNull FoldingModelEx foldingModel,
                                    FoldingUpdate.FoldingMap elementsToFold, @NotNull Map<TextRange, Boolean> rangeToExpandStatusMap) {
    List<FoldRegion> toRemove = newArrayList();
    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(myProject);
    for (FoldRegion region : foldingModel.getAllFoldRegions()) {
      if (myKeepCollapsedRegions && !region.isExpanded() && !regionOrGroupCanBeRemovedWhenCollapsed(region)) continue;
      
      PsiElement element = info.getPsiElement(region);
      if (element != null) {
        PsiFile containingFile = element.getContainingFile();
        boolean isInjected = injectedManager.isInjectedFragment(containingFile);
        if (isInjected != myForInjected) continue;
      }
      final Collection<FoldingDescriptor> descriptors;
      if (element != null && !(descriptors = elementsToFold.get(element)).isEmpty()) {
        boolean matchingDescriptorFound = false;
        FoldingDescriptor[] array = descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
        for (FoldingDescriptor descriptor : array) {
          TextRange range = descriptor.getRange();
          if (TextRange.areSegmentsEqual(region, range)) {
            matchingDescriptorFound = true;
            if (!region.isValid() ||
                region.getGroup() != null ||
                descriptor.getGroup() != null ||
                !region.getPlaceholderText().equals(descriptor.getPlaceholderText()) ||
                range.getLength() < 2
              ) {
              rangeToExpandStatusMap.put(range, region.isExpanded());
              toRemove.add(region);
              break;
            }
            else {
              elementsToFold.remove(element, descriptor);
            }
          }
        }
        if (!matchingDescriptorFound) {
          if (Registry.is("editor.durable.folding.state")) {
            for (FoldingDescriptor descriptor : descriptors) {
              rangeToExpandStatusMap.put(descriptor.getRange(), region.isExpanded());
            }
          }
          toRemove.add(region);
        }
      }
      else if (region.isValid() && info.isLightRegion(region)) {
        boolean isExpanded = region.isExpanded();
        rangeToExpandStatusMap.put(TextRange.create(region), isExpanded);
      }
      else {
        toRemove.add(region);
      }
    }

    for (final FoldRegion region : toRemove) {
      foldingModel.removeFoldRegion(region);
      info.removeRegion(region);
    }
  }

  private boolean regionOrGroupCanBeRemovedWhenCollapsed(FoldRegion region) {
    FoldingGroup group = region.getGroup();
    List<FoldRegion> affectedRegions = group != null && myEditor instanceof EditorEx
                                       ? ((EditorEx)myEditor).getFoldingModel().getGroupedRegions(group)
                                       : Collections.singletonList(region);
    for (FoldRegion affectedRegion : affectedRegions) {
      if (regionCanBeRemovedWhenCollapsed(affectedRegion)) return true;
    }
    return false;
  }

  private boolean regionCanBeRemovedWhenCollapsed(FoldRegion region) {
    return Boolean.TRUE.equals(region.getUserData(CAN_BE_REMOVED_WHEN_COLLAPSED)) ||
           ((FoldingModelEx)myEditor.getFoldingModel()).hasDocumentRegionChangedFor(region) ||
           !region.isValid() ||
           isRegionInCaretLine(region);
  }

  private boolean isRegionInCaretLine(FoldRegion region) {
    int regionStartLine = myEditor.getDocument().getLineNumber(region.getStartOffset());
    int regionEndLine = myEditor.getDocument().getLineNumber(region.getEndOffset());
    int caretLine = myEditor.getCaretModel().getLogicalPosition().line;
    return caretLine >= regionStartLine && caretLine <= regionEndLine;
  }
}
