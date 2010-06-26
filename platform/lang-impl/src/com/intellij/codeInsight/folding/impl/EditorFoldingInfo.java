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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class EditorFoldingInfo {
  private static final Key<EditorFoldingInfo> KEY = Key.create("EditorFoldingInfo.KEY");

  private final Map<FoldRegion, PsiElement> myFoldRegionToSmartPointerMap = new THashMap<FoldRegion, PsiElement>();

  public static EditorFoldingInfo get(@NotNull Editor editor) {
    EditorFoldingInfo info = editor.getUserData(KEY);
    if (info == null){
      info = new EditorFoldingInfo();
      editor.putUserData(KEY, info);
    }
    return info;
  }

  public PsiElement getPsiElement(@NotNull FoldRegion region) {
    final PsiElement element = myFoldRegionToSmartPointerMap.get(region);
    return element != null && element.isValid() ? element:null;
  }
  public TextRange getPsiElementRange(@NotNull FoldRegion region) {
    PsiElement element = getPsiElement(region);
    if (element == null) return null;
    PsiFile containingFile = element.getContainingFile();
    InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(containingFile.getProject());
    boolean isInjected = injectedManager.isInjectedFragment(containingFile);
    TextRange range = element.getTextRange();
    if (isInjected) {
      range = injectedManager.injectedToHost(element, range);
    }
    return range;
  }

  public boolean isLightRegion(@NotNull FoldRegion region) {
    return myFoldRegionToSmartPointerMap.get(region) == null;
  }

  public void addRegion(@NotNull FoldRegion region, @NotNull FoldingDescriptor element){
    myFoldRegionToSmartPointerMap.put(region, element.getElement().getPsi());
  }

  public void removeRegion(@NotNull FoldRegion region){
    myFoldRegionToSmartPointerMap.remove(region);
  }

  public void dispose() {
    myFoldRegionToSmartPointerMap.clear();
  }

  public static void resetInfo(final Editor editor) {
    EditorFoldingInfo info = editor.getUserData(KEY);
    if (info != null) {
      final DocumentEx document = (DocumentEx)editor.getDocument();
      for(FoldRegion region:info.myFoldRegionToSmartPointerMap.keySet()) {
        document.removeRangeMarker((RangeMarkerEx)region);
      }
    }
    editor.putUserData(KEY, null);
  }
}
