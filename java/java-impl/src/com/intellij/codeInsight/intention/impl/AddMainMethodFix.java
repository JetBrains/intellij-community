// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.java.JavaBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AddMainMethodFix extends PsiUpdateModCommandAction<PsiImplicitClass> implements UserDataHolder {
  public AddMainMethodFix(@NotNull PsiImplicitClass element) {
    super(element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.add.main.method");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiImplicitClass element, @NotNull ModPsiUpdater updater) {
    PsiMethod mainMethod = JavaPsiFacade.getInstance(context.project()).getElementFactory().createMethod("main", PsiTypes.voidType());
    mainMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, false);
    element.add(mainMethod);
  }

  /**
   * this method is used not to show gear icons. See {@link FileLevelIntentionComponent#FileLevelIntentionComponent(String, HighlightSeverity, GutterMark, List, PsiFile, Editor, String)}
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    if(key.equals(IntentionManager.SHOW_INTENTION_OPTIONS_KEY)) return (T)Boolean.FALSE;
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }
}
