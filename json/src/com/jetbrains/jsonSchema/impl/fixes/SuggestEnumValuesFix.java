// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SuggestEnumValuesFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
  public SuggestEnumValuesFix() {
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with allowed value";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof JsonValue)) return;
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(element.getContainingFile().getVirtualFile());
    boolean whitespaceBefore = false;
    if (element.getPrevSibling() instanceof PsiWhiteSpace) {
      whitespaceBefore = true;
    }
    WriteAction.run(() -> element.delete());
    EditorEx editor = EditorUtil.getEditorEx(fileEditor);
    assert editor != null;
    if (whitespaceBefore) {
      WriteAction.run(() -> {
        int offset = editor.getCaretModel().getOffset();
        editor.getDocument().insertString(offset, " ");
        editor.getCaretModel().moveToOffset(offset + 1);
      });
    }
    CodeCompletionHandlerBase.createHandler(CompletionType.BASIC).invokeCompletion(project, editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project,
                       @NotNull CommonProblemDescriptor[] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      HintManager.getInstance().showErrorHint(editor, "Sorry, this fix is not available in batch mode");
    }
    else {
      Messages.showErrorDialog(project, "Sorry, this fix is not available in batch mode", "Not Applicable in Batch Mode");
    }
  }
}
