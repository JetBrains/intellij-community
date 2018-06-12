// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Holds {@code 'fold region -> PSI element'} mappings.
 * <p/>
 * Not thread-safe.
 */
public class EditorFoldingInfo {
  private static final Key<EditorFoldingInfo> KEY = Key.create("EditorFoldingInfo.KEY");

  private final Map<FoldRegion, SmartPsiElementPointer<?>> myFoldRegionToSmartPointerMap = new THashMap<>();

  @NotNull
  public static EditorFoldingInfo get(@NotNull Editor editor) {
    EditorFoldingInfo info = editor.getUserData(KEY);
    if (info == null){
      info = new EditorFoldingInfo();
      editor.putUserData(KEY, info);
    }
    return info;
  }

  @Nullable
  public PsiElement getPsiElement(@NotNull FoldRegion region) {
    final SmartPsiElementPointer<?> pointer = myFoldRegionToSmartPointerMap.get(region);
    if (pointer == null) {
      return null;
    }
    PsiElement element = pointer.getElement();
    return element != null && element.isValid() ? element : null;
  }

  @Nullable
  TextRange getPsiElementRange(@NotNull FoldRegion region) {
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

  void addRegion(@NotNull FoldRegion region, @NotNull SmartPsiElementPointer<?> pointer){
    myFoldRegionToSmartPointerMap.put(region, pointer);
  }

  public void removeRegion(@NotNull FoldRegion region){
    myFoldRegionToSmartPointerMap.remove(region);
  }

  public void dispose() {
    myFoldRegionToSmartPointerMap.clear();
  }
}
