package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import static com.intellij.util.containers.CollectionFactory.arrayList;
import static com.intellij.util.containers.CollectionFactory.newTroveMap;
import com.intellij.util.containers.HashMap;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
* @author cdr
*/
class UpdateFoldRegionsOperation implements Runnable {
  private final Editor myEditor;
  private final boolean myApplyDefaultState;
  private final TreeMap<PsiElement, FoldingDescriptor> myElementsToFoldMap;

  UpdateFoldRegionsOperation(Editor editor, TreeMap<PsiElement, FoldingDescriptor> elementsToFoldMap, boolean applyDefaultState) {
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
      }
      else {
        expanded = shouldExpand.get(region);
      }

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

    for (final Map.Entry<PsiElement, FoldingDescriptor> entry : myElementsToFoldMap.entrySet()) {
      ProgressManager.getInstance().checkCanceled();
      PsiElement element = entry.getKey();
      final FoldingDescriptor descriptor = entry.getValue();
      FoldingGroup group = descriptor.getGroup();
      TextRange range = descriptor.getRange();
      FoldRegion region = new FoldRegionImpl(myEditor, range.getStartOffset(), range.getEndOffset(), descriptor.getPlaceholderText(), group);
      if (!foldingModel.addFoldRegion(region)) continue;

      info.addRegion(region, descriptor);
      newRegions.add(region);

      boolean expandStatus = shouldExpandNewRegion(element, range, rangeToExpandStatusMap);
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
    boolean caretInside = FoldingUtil.caretInsideRange(myEditor, range);
    if (myApplyDefaultState) {
      return caretInside || !FoldingPolicy.isCollapseByDefault(element);
    }

    final Boolean oldStatus = rangeToExpandStatusMap.get(range);
    return oldStatus == null || caretInside || oldStatus.booleanValue();
  }

  private void removeInvalidRegions(EditorFoldingInfo info, FoldingModelEx foldingModel, HashMap<TextRange, Boolean> rangeToExpandStatusMap) {
    List<FoldRegion> toRemove = arrayList();
    for (FoldRegion region : foldingModel.getAllFoldRegions()) {
      PsiElement element = info.getPsiElement(region);
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
