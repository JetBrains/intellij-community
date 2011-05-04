/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.util.containers.HashMap;

import java.util.List;
import java.util.Map;

import static com.intellij.util.containers.CollectionFactory.arrayList;
import static com.intellij.util.containers.CollectionFactory.newTroveMap;

/**
 * @author cdr
 */
class UpdateFoldRegionsOperation implements Runnable {
  
  static final Key<Boolean> ALLOW_FOLDING_ON_CARET_LINE_KEY = Key.create("AllowFoldingOnCaretLine.KEY");
  
  private final Project myProject;
  private final Editor myEditor;
  private final boolean myApplyDefaultState;
  private final Map<PsiElement, FoldingDescriptor> myElementsToFoldMap;
  private final boolean myForInjected;

  UpdateFoldRegionsOperation(Project project, Editor editor, Map<PsiElement, FoldingDescriptor> elementsToFoldMap, boolean applyDefaultState,
                             boolean forInjected) {
    myProject = project;
    myEditor = editor;
    myApplyDefaultState = applyDefaultState;
    myElementsToFoldMap = elementsToFoldMap;
    myForInjected = forInjected;
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
    
    // Reset the key.
    myEditor.putUserData(ALLOW_FOLDING_ON_CARET_LINE_KEY, false);
  }

  private static void applyExpandStatus(List<FoldRegion> newRegions, Map<FoldRegion, Boolean> shouldExpand, Map<FoldingGroup, Boolean> groupExpand) {
    for (final FoldRegion region : newRegions) {
      final FoldingGroup group = region.getGroup();
      final Boolean expanded = group == null ? shouldExpand.get(region) : groupExpand.get(group);

      if (expanded != null) {
        region.setExpanded(expanded.booleanValue());
      }
    }
  }

  private List<FoldRegion> addNewRegions(EditorFoldingInfo info,
                                         FoldingModelEx foldingModel,
                                         Map<TextRange, Boolean> rangeToExpandStatusMap,
                                         Map<FoldRegion, Boolean> shouldExpand,
                                         Map<FoldingGroup, Boolean> groupExpand) {
    List<FoldRegion> newRegions = arrayList();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);

    for (final Map.Entry<PsiElement, FoldingDescriptor> entry : myElementsToFoldMap.entrySet()) {
      ProgressManager.checkCanceled();
      PsiElement element = entry.getKey();
      final FoldingDescriptor descriptor = entry.getValue();
      FoldingGroup group = descriptor.getGroup();
      TextRange range = descriptor.getRange();
      String placeholder = descriptor.getPlaceholderText();
      FoldRegion region = foldingModel.createFoldRegion(range.getStartOffset(), range.getEndOffset(),
                                                        placeholder == null ? "..." : placeholder,
                                                        group,
                                                        descriptor.isNonExpandable());
      if (region == null) continue;

      PsiElement psi = descriptor.getElement().getPsi();
      
      if (psi == null || !psi.isValid() || !foldingModel.addFoldRegion(region)) {
        region.dispose();
        continue;
      }
      
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
    return newRegions;
  }

  private boolean shouldExpandNewRegion(PsiElement element, TextRange range, Map<TextRange, Boolean> rangeToExpandStatusMap) {
    boolean caretInside;
    if (myEditor.getUserData(ALLOW_FOLDING_ON_CARET_LINE_KEY) == Boolean.TRUE) {
      caretInside = FoldingUtil.caretInsideRange(myEditor, range);
    }
    else {
      final Document document = myEditor.getDocument();
      final int firstLine = document.getLineNumber(range.getStartOffset());
      final int lastLine = document.getLineNumber(range.getEndOffset());
      int caretOffset = myEditor.getCaretModel().getOffset();
      if (caretOffset > myEditor.getDocument().getTextLength()) {
        return false;
      }
      final int currentLine = document.getLineNumber(caretOffset);
      caretInside = firstLine <= currentLine && currentLine <= lastLine;
    }

    if (myApplyDefaultState) {
      return caretInside || !FoldingPolicy.isCollapseByDefault(element);
    }

    final Boolean oldStatus = rangeToExpandStatusMap.get(range);
    return oldStatus == null || caretInside || oldStatus.booleanValue();
  }

  private void removeInvalidRegions(EditorFoldingInfo info, FoldingModelEx foldingModel, HashMap<TextRange, Boolean> rangeToExpandStatusMap) {
    List<FoldRegion> toRemove = arrayList();
    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(myProject);
    for (FoldRegion region : foldingModel.getAllFoldRegions()) {
      PsiElement element = info.getPsiElement(region);
      if (element != null) {
        PsiFile containingFile = element.getContainingFile();
        boolean isInjected = injectedManager.isInjectedFragment(containingFile);
        if (isInjected != myForInjected) continue;
      }
      if (element != null && myElementsToFoldMap.containsKey(element)) {
        final FoldingDescriptor descriptor = myElementsToFoldMap.get(element);
        TextRange range = descriptor.getRange();
        if (!region.isValid() ||
            region.getGroup() != null ||
            descriptor.getGroup() != null ||
            region.getStartOffset() != range.getStartOffset() ||
            region.getEndOffset() != range.getEndOffset() ||
            !region.getPlaceholderText().equals(descriptor.getPlaceholderText()) ||
            range.getLength() < 2
          ) {
          rangeToExpandStatusMap.put(range, region.isExpanded());
          toRemove.add(region);
        }
        else {
          myElementsToFoldMap.remove(element);
        }
      }
      else if (region.isValid() && info.isLightRegion(region)) {
        boolean isExpanded = region.isExpanded();
        rangeToExpandStatusMap.put(TextRange.create(region),
                                   isExpanded ? Boolean.TRUE : Boolean.FALSE);
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

}
