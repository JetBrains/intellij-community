// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.intention.impl.QuickEditAction;
import com.intellij.codeInsight.intention.impl.QuickEditHandler;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformUtils;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class CheckRegExpIntentionAction extends QuickEditAction implements Iconable {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (PlatformUtils.isPyCharmEducational()) {
      return false;
    }

    if (editor.getUserData(CheckRegExpForm.Keys.CHECK_REG_EXP_EDITOR) != null) {
      // to disable intention inside CheckRegExpForm itself
      return false;
    }
    Pair<PsiElement, TextRange> pair = getRangePair(psiFile, editor);
    if (pair != null && pair.first != null) {
      Language language = pair.first.getLanguage();
      return language.isKindOf(RegExpLanguage.INSTANCE);
    }
    PsiFile baseFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(psiFile);
    return baseFile != null && baseFile.getLanguage().isKindOf(RegExpLanguage.INSTANCE);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    PsiFile baseFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(psiFile);
    if (baseFile == null || !baseFile.getLanguage().isKindOf(RegExpLanguage.INSTANCE)) {
      super.invoke(project, editor, psiFile);
      return;
    }
    JComponent component = createBalloonComponent(psiFile);
    if (component != null) QuickEditHandler.showBalloon(editor, psiFile, component);
  }

  @Override
  protected boolean isShowInBalloon() {
    return true;
  }

  @Override
  protected JComponent createBalloonComponent(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      return new CheckRegExpForm(file).getRootPanel();
    }
    return null;
  }

  @Override
  public @NotNull String getText() {
    return RegExpBundle.message("intention.name.check.regexp");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public Icon getIcon(int flags) {
    //noinspection ConstantConditions
    return RegExpLanguage.INSTANCE.getAssociatedFileType().getIcon();
  }
}
