// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavadocSnippetEnterHandler extends EnterHandlerDelegateAdapter {

  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!(file instanceof PsiJavaFile) || !file.isValid()) return Result.Continue;

    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
    final PsiSnippetDocTag host = ObjectUtils.tryCast(injectedLanguageManager.getInjectionHost(file), PsiSnippetDocTag.class);

    return host == null ? Result.Continue : Result.Default;
  }

  @Override
  public Result postProcessEnter(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull DataContext dataContext) {
    final PsiSnippetDocTag host = getHost(file, editor);
    if (host == null) return Result.Continue;

    final Editor hostEditor = ((EditorWindow)editor).getDelegate();
    final Document hostDocument = hostEditor.getDocument();

    final CaretModel caretModelHost = hostEditor.getCaretModel();
    int caretOffsetHost = caretModelHost.getOffset();

    final int lineStartOffset = DocumentUtil.getLineStartOffset(caretOffsetHost, hostDocument);
    int firstNonWsLineOffset = CharArrayUtil.shiftForward(hostDocument.getText(), lineStartOffset, " \t");

    if (hostDocument.getText().charAt(firstNonWsLineOffset) != '*') {
      final String prefix = calcPrefix(host, PsiUtil.isInMarkdownDocComment(InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file)));
      hostDocument.insertString(lineStartOffset, prefix);
      caretModelHost.moveToOffset(caretOffsetHost + prefix.length());
      EditorModificationUtilEx.scrollToCaret(editor);
    }

    return Result.Default;
  }

  private static @Nullable PsiSnippetDocTag getHost(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!(file instanceof PsiJavaFile) || !file.isValid()) return null;

    if (!(editor instanceof EditorWindow)) {
      return null;
    }

    final SmartPsiElementPointer<?> data = file.getUserData(FileContextUtil.INJECTED_IN_ELEMENT);
    if (data == null) return null;

    final PsiSnippetDocTagImpl host = ObjectUtils.tryCast(data.getElement(), PsiSnippetDocTagImpl.class);
    if (host == null) return null;
    return host;
  }

  private static String calcPrefix(PsiSnippetDocTag host, boolean markdownComment) {
    final PsiFile file = host.getContainingFile();
    final String text = file.getText();

    int offset = host.getTextOffset();
    int asteriskOffset = offset;
    while (text.charAt(offset) != '\n' && offset > 0) {
      if (text.charAt(offset) == '*') asteriskOffset = offset;
      offset --;
    }

    final String whitespacesPrefix = text.substring(offset + 1, asteriskOffset);

    final JavaCodeStyleSettings settings = CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class);

    return (settings.JD_LEADING_ASTERISKS_ARE_ENABLED && !markdownComment) ? whitespacesPrefix + "* " : whitespacesPrefix;
  }

}