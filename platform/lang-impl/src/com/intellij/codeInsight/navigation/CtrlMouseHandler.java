// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated
@ApiStatus.Internal
public final class CtrlMouseHandler {
  static final Logger LOG = Logger.getInstance(CtrlMouseHandler.class);

  private static @Nullable CtrlMouseAction getCtrlMouseAction(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    return action instanceof CtrlMouseAction ? (CtrlMouseAction)action : null;
  }

  @TestOnly
  public static @Nullable String getInfo(PsiElement element, PsiElement atPointer) {
    return SingleTargetElementInfo.generateInfo(element, atPointer, true).text;
  }

  @TestOnly
  public static @Nullable String getGoToDeclarationOrUsagesText(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    CtrlMouseInfo ctrlMouseInfo = getCtrlMouseInfo(ACTION_GOTO_DECLARATION, editor, file, editor.getCaretModel().getOffset());
    return ctrlMouseInfo == null ? null : ctrlMouseInfo.getDocInfo().text;
  }

  @ApiStatus.Internal
  public static @Nullable CtrlMouseInfo getCtrlMouseInfo(@NotNull String actionId,
                                                         @NotNull Editor editor,
                                                         @NotNull PsiFile file,
                                                         int offset) {
    CtrlMouseAction action = getCtrlMouseAction(actionId);
    if (action == null) {
      return null;
    }
    return action.getCtrlMouseInfo(editor, file, offset);
  }
}
