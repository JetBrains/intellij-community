// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SliceHandler implements CodeInsightActionHandler {
  final boolean myDataFlowToThis;

  SliceHandler(boolean dataFlowToThis) {
    myDataFlowToThis = dataFlowToThis;
  }

  @Override
  public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    PsiElement expression = getExpressionAtCaret(editor, file);
    if (expression == null) {
      HintManager.getInstance().showErrorHint(editor, LangBundle.message("hint.text.cannot.find.what.to.analyze"));
      return;
    }

    SliceManager sliceManager = SliceManager.getInstance(project);
    sliceManager.slice(expression,myDataFlowToThis, this);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public @Nullable PsiElement getExpressionAtCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    if (offset == 0) {
      return null;
    }
    PsiElement atCaret = file.findElementAt(offset);

    SliceLanguageSupportProvider provider = LanguageSlicing.getProvider(file);
    if (provider == null || atCaret == null) {
      return null;
    }
    PsiElement expression = provider.getExpressionAtCaret(atCaret, myDataFlowToThis);
    if (expression != null && !expression.isPhysical()) {
      return null;
    }
    return expression;
  }

  public abstract SliceAnalysisParams askForParams(@NotNull PsiElement element,
                                                   @NotNull SliceManager.StoredSettingsBean storedSettingsBean,
                                                   @NotNull @NlsContexts.DialogTitle String dialogTitle);

  @Contract("_ -> new")
  public static @NotNull SliceHandler create(boolean dataFlowToThis) {
    return dataFlowToThis ? new SliceBackwardHandler() : new SliceForwardHandler();
  }
}
