package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.HashMap;

import java.util.Map;

class EditorFoldingInfo {
  private static final Key KEY = Key.create("EditorFoldingInfo.KEY");

  private Map<FoldRegion, SmartPsiElementPointer> myFoldRegionToSmartPointerMap = new HashMap<FoldRegion, SmartPsiElementPointer>();

  public static EditorFoldingInfo get(Editor editor) {
    EditorFoldingInfo info = (EditorFoldingInfo)editor.getUserData(KEY);
    if (info == null){
      info = new EditorFoldingInfo();
      editor.putUserData(KEY, info);
    }
    return info;
  }

  public PsiElement getPsiElement(FoldRegion region) {
    SmartPsiElementPointer pointer = myFoldRegionToSmartPointerMap.get(region);
    if (pointer != null) {
      return pointer.getElement();
    }
    return null;
  }

  public boolean isLightRegion(FoldRegion region) {
    return myFoldRegionToSmartPointerMap.get(region) == null;
  }

  public void addRegion(FoldRegion region, PsiElement element){
    Project project = element.getProject();
    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element);
    myFoldRegionToSmartPointerMap.put(region, pointer);
  }

  public void removeRegion(FoldRegion region){
    myFoldRegionToSmartPointerMap.remove(region);
  }

  public void dispose() {
    myFoldRegionToSmartPointerMap.clear();
  }
}
