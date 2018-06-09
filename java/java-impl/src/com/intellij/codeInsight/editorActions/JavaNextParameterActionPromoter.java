// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;

import java.util.List;

// Prevents 'tab out' action from taking preference when caret is before completed method call's closing parenthesis
public class JavaNextParameterActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(List<AnAction> actions, DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) return null;
    if (!ParameterInfoController.existsForEditor(editor)) return null;
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;
    int caretOffset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiElement argumentList = ParameterInfoController.findArgumentList(file, caretOffset, -1);
    if (argumentList == null) return null;
    int lbraceOffset = argumentList.getTextRange().getStartOffset();
    if (ParameterInfoController.findControllerAtOffset(editor, lbraceOffset) == null) return null;
    int rbraceOffset = argumentList.getTextRange().getEndOffset() - 1;
    if (caretOffset > rbraceOffset ||
        !CharArrayUtil.containsOnlyWhiteSpaces(document.getImmutableCharSequence().subSequence(caretOffset, rbraceOffset))) return null;
    return ContainerUtil.filter(actions, action -> !(action instanceof BraceOrQuoteOutAction));
  }
}
