// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeWithMe.ClientId;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class NextPrevParameterHandler extends EditorWriteActionHandler.ForEachCaret {
  private static final LanguageExtension<TemplateParameterTraversalPolicy>
    EP = new LanguageExtension<>("com.intellij.templateParameterTraversalPolicy");

  protected final EditorActionHandler originalHandler;
  protected final boolean next;

  public NextPrevParameterHandler(EditorActionHandler originalHandler, boolean next) {
    this.originalHandler = originalHandler;
    this.next = next;
  }

  public static boolean hasSuitablePolicy(Editor editor, PsiFile file) {
    return findSuitableTraversalPolicy(editor, file) != null;
  }

  public static boolean hasSuitablePolicy(PsiFile file) {
    return findPolicyForFile(file) != null;
  }

  static @Nullable TemplateParameterTraversalPolicy findSuitableTraversalPolicy(Editor editor, PsiFile file) {
    TemplateParameterTraversalPolicy policy = findPolicyForFile(file);
    return policy != null && policy.isValidForFile(editor, file) ? policy : null;
  }

  private static TemplateParameterTraversalPolicy findPolicyForFile(PsiFile file) {
    return EP.forLanguage(file.getLanguage());
  }

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (originalHandler == null) return false;
    return originalHandler.isEnabled(editor, caret, dataContext);
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor,
                                 @NotNull Caret caret,
                                 DataContext dataContext) {
    if (!handleTab(editor, dataContext, next)) {
      originalHandler.execute(editor, caret, dataContext);
    }
  }

  private static boolean handleTab(@NotNull Editor editor, DataContext dataContext, boolean next) {
    if (editor.getCaretModel().getCaretCount() > 1) return false;
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null && ClientId.isCurrentlyUnderLocalId()) {
      Shortcut @NotNull []
        shortcuts = keymapManager.getActiveKeymap().getShortcuts(next ? "NextTemplateParameter" : "PrevTemplateParameter");
      if (shortcuts.length > 0) {
        return false;
      }
    }
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    PsiFile psiFile = dataContext.getData(CommonDataKeys.PSI_FILE);
    if (project == null || psiFile == null) return false;
    TemplateParameterTraversalPolicy policy = findSuitableTraversalPolicy(editor, psiFile);
    if (policy != null) {
      policy.invoke(editor, psiFile, next);
      return true;
    }
    else {
      return false;
    }
  }
}

class NextParameterHandler extends NextPrevParameterHandler {

  NextParameterHandler(EditorActionHandler originalHandler) {
    super(originalHandler, true);
  }
}

class PrevParameterHandler extends NextPrevParameterHandler {

  PrevParameterHandler(EditorActionHandler originalHandler) {
    super(originalHandler, false);
  }
}