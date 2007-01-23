package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

import java.util.Map;

class EditorFoldingInfo {
  private static final Key<EditorFoldingInfo> KEY = Key.create("EditorFoldingInfo.KEY");

  private Map<FoldRegion, PsiElement> myFoldRegionToSmartPointerMap = new HashMap<FoldRegion, PsiElement>();

  public static EditorFoldingInfo get(Editor editor) {
    EditorFoldingInfo info = editor.getUserData(KEY);
    if (info == null){
      info = new EditorFoldingInfo();
      editor.putUserData(KEY, info);
    }
    return info;
  }

  public PsiElement getPsiElement(FoldRegion region) {
    final PsiElement element = myFoldRegionToSmartPointerMap.get(region);
    return element != null && element.isValid() ? element:null;
  }

  public boolean isLightRegion(FoldRegion region) {
    return myFoldRegionToSmartPointerMap.get(region) == null;
  }

  public void addRegion(FoldRegion region, PsiElement element){
    myFoldRegionToSmartPointerMap.put(region, element);
  }

  public void removeRegion(FoldRegion region){
    myFoldRegionToSmartPointerMap.remove(region);
  }

  public void dispose() {
    myFoldRegionToSmartPointerMap.clear();
  }
}
