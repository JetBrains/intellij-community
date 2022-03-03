// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class JavadocSnippetEnterHandler extends EnterHandlerDelegateAdapter {

  @Override
  public Result postProcessEnter(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull DataContext dataContext) {
    if (!(file instanceof PsiJavaFile) || !file.isValid()) return Result.Continue;

    final CaretModel caretModel = editor.getCaretModel();
    int caretOffset = caretModel.getOffset();

    final PsiElement current = file.findElementAt(caretOffset);
    if (current == null) return Result.Continue;

    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
    final PsiSnippetDocTag host = ObjectUtils.tryCast(injectedLanguageManager.getInjectionHost(current), PsiSnippetDocTag.class);
    if (host == null) return Result.Continue;

    final Document document = editor.getDocument();
    final String prefix = calcPrefix(host);

    final int lineStartOffset = DocumentUtil.getLineStartOffset(caretOffset, document);
    document.insertString(lineStartOffset, prefix);

    caretModel.moveToOffset(caretOffset + prefix.length());
    EditorModificationUtilEx.scrollToCaret(editor);

    return Result.Continue;
  }

  private static String calcPrefix(PsiSnippetDocTag host) {
    final PsiFile file = host.getContainingFile();
    final String text = file.getText();

    int offset = host.getTextOffset();
    int asteriskOffset = offset;
    while (text.charAt(offset) != '\n' && offset > 0) {
      if (text.charAt(offset) == '*') asteriskOffset = offset;
      offset --;
    }

    final String prefix = text.substring(offset + 1, asteriskOffset);

    final JavaCodeStyleSettings settings = CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class);

    return settings.JD_LEADING_ASTERISKS_ARE_ENABLED ? prefix + "*" : prefix;
  }

}