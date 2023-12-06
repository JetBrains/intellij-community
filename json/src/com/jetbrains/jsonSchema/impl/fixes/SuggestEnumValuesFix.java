// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonProperty;
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
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SuggestEnumValuesFix implements LocalQuickFix, BatchQuickFix {
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public SuggestEnumValuesFix(JsonLikeSyntaxAdapter quickFixAdapter) {
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JsonBundle.message("replace.with.allowed.value");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
    return getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement initialElement = descriptor.getPsiElement();
    PsiElement element = myQuickFixAdapter.adjustValue(initialElement);
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(element.getContainingFile().getVirtualFile());
    boolean whitespaceBefore = false;
    PsiElement prevPrev = null;
    PsiElement prev = element.getPrevSibling();
    if (prev instanceof PsiWhiteSpace) {
      whitespaceBefore = true;
      prevPrev = prev.getPrevSibling();
    }
    boolean shouldAddWhitespace = myQuickFixAdapter.fixWhitespaceBefore(initialElement, element);
    PsiElement parent = element.getParent();
    boolean isJsonPropName = parent instanceof JsonProperty && ((JsonProperty)parent).getNameElement() == element;
    if (isJsonPropName) {
      WriteAction.run(() -> element.replace(new JsonElementGenerator(project).createStringLiteral("")));
    }
    else {
      WriteAction.run(() -> element.delete());
    }
    EditorEx editor = EditorUtil.getEditorEx(fileEditor);
    assert editor != null;
    // this is a workaround for buggy formatters such as in YAML - it removes the whitespace after ':' when deleting the value
    shouldAddWhitespace |= prevPrev != null && PsiUtilCore.getElementType(prevPrev.getNextSibling()) != TokenType.WHITE_SPACE;
    if (shouldAddWhitespace && whitespaceBefore) {
      WriteAction.run(() -> {
        int offset = editor.getCaretModel().getOffset();
        editor.getDocument().insertString(offset, " ");
        editor.getCaretModel().moveToOffset(offset + 1);
      });
    }
    if (isJsonPropName) {
      editor.getCaretModel().moveToOffset(((JsonProperty)parent).getNameElement().getTextOffset() + 1);
    }
    CodeCompletionHandlerBase.createHandler(CompletionType.BASIC).invokeCompletion(project, editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      HintManager.getInstance().showErrorHint(editor, JsonBundle.message("sorry.this.fix.is.not.available.in.batch.mode"));
    }
    else {
      Messages.showErrorDialog(project, JsonBundle.message("sorry.this.fix.is.not.available.in.batch.mode"),
                               JsonBundle.message("not.applicable.in.batch.mode"));
    }
  }
}
