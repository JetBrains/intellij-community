// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public abstract class CreateFromUsageBaseFix extends BaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return getAvailableText(project, editor.getCaretModel().getOffset()) != null;
  }

  /**
   * @param project the project of the file
   * @param offset  the offset of the caret
   * @return the text of the fix when the fix applies at the offset, or null when it does not apply
   */
  protected final @IntentionName @Nullable String getAvailableText(@NotNull Project project, int offset) {
    PsiElement element = getElement();
    if (element == null || isValidElement(element)) {
      return null;
    }

    if (!isAvailableImpl(offset)) {
      return null;
    }

    List<PsiClass> targetClasses = CreateFromUsageUtils.filterTargetClasses(
      CreateFromUsageUtils.getTargetClasses(element, false, _ -> false), project);
    if (targetClasses.isEmpty()) {
      return null;
    }
    return getText();
  }

  protected abstract boolean isAvailableImpl(int offset);

  protected abstract boolean isValidElement(PsiElement result);

  protected abstract @Nullable PsiElement getElement();

  public static void startTemplate(@NotNull Editor editor, final Template template, final @NotNull Project project) {
    startTemplate(editor, template, project, null);
  }

  protected static void startTemplate(final @NotNull Editor editor,
                                      final Template template,
                                      final @NotNull Project project,
                                      final TemplateEditingListener listener) {
    startTemplate(editor, template, project, listener, null);
  }

  public static void startTemplate(final @NotNull Editor editor,
                                   final Template template,
                                   final @NotNull Project project,
                                   final TemplateEditingListener listener,
                                   final @NlsContexts.Command String commandName) {
    Runnable runnable = () -> TemplateManager.getInstance(project).startTemplate(editor, template, listener);
    if (!ApplicationManager.getApplication().isWriteIntentLockAcquired() || IntentionPreviewUtils.isIntentionPreviewActive()) {
      runnable.run();
    } else {
      CommandProcessor.getInstance().executeCommand(project, runnable, commandName, commandName);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void startTemplate(@NotNull Project project, @NotNull PsiClass aClass, @NotNull Template template, @Nls @NotNull String text) {
    aClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(aClass);
    template.setToReformat(true);

    final Editor editor = CodeInsightUtil.positionCursor(project, Objects.requireNonNull(aClass).getContainingFile(), aClass);
    if (editor == null) return;

    Segment textRange = aClass.getTextRange();
    editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
    startTemplate(editor, template, project, null, text);
  }
}
