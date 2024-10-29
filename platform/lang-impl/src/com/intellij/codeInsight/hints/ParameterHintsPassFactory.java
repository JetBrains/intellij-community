// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterHintsPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  private static final Key<Long> PSI_MODIFICATION_STAMP = Key.create("psi.modification.stamp");

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    boolean serialized = ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    int[] ghl = serialized ? new int[]{Pass.UPDATE_ALL} : null;
    registrar.registerTextEditorHighlightingPass(this, ghl, null, false, -1);
  }

  @Override
  public @Nullable TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    if (editor.isOneLineMode()) return null;
    long currentStamp = getCurrentModificationStamp(file);
    Long savedStamp = editor.getUserData(PSI_MODIFICATION_STAMP);
    if (savedStamp != null && savedStamp == currentStamp) return null;
    Language language = file.getLanguage();
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider == null) return null;
    return new ParameterHintsPass(file, editor, MethodInfoExcludeListFilter.forLanguage(language), false);
  }

  public static long getCurrentModificationStamp(@NotNull PsiFile file) {
    return file.getManager().getModificationTracker().getModificationCount();
  }

  public static void forceHintsUpdateOnNextPass() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      forceHintsUpdateOnNextPass(editor);
    }
  }

  public static void forceHintsUpdateOnNextPass(@NotNull Editor editor) {
    editor.putUserData(PSI_MODIFICATION_STAMP, null);
  }

  protected static void putCurrentPsiModificationStamp(@NotNull Editor editor, @NotNull PsiFile file) {
    editor.putUserData(PSI_MODIFICATION_STAMP, getCurrentModificationStamp(file));
  }
}