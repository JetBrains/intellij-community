// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.codeInsight.navigation.CtrlMouseHandlerKt.getCtrlMouseData;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION;

@ApiStatus.Internal
public final class CtrlMouseHandler {

  @TestOnly
  public static @Nullable String getInfo(PsiElement element, PsiElement atPointer) {
    return SingleTargetElementInfo.generateInfo(element, atPointer, true);
  }

  @TestOnly
  public static @Nullable String getGoToDeclarationOrUsagesText(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    CtrlMouseData data = getCtrlMouseData(ACTION_GOTO_DECLARATION, editor, file, editor.getCaretModel().getOffset());
    return data == null ? null : data.getHintText();
  }
}
