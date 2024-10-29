// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Please, don't extend the class.
 * Use the {@code EnterBetweenBracesDelegate} language-specific implementation instead.
 */
public class EnterBetweenBracesFinalHandler extends EnterHandlerDelegateAdapter {
  @Override
  public Result preprocessEnter(final @NotNull PsiFile file,
                                @NotNull Editor editor,
                                final @NotNull Ref<Integer> caretOffsetRef,
                                final @NotNull Ref<Integer> caretAdvance,
                                final @NotNull DataContext dataContext,
                                final EditorActionHandler originalHandler) {
    if (!CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
      return Result.Continue;
    }
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int caretOffset = caretOffsetRef.get().intValue();

    final EnterBetweenBracesDelegate helper = getLanguageImplementation(EnterHandler.getLanguage(dataContext));
    if (!isApplicable(file, editor, text, caretOffset, helper)) {
      return Result.Continue;
    }

    final Data data = new Data(file, document, caretOffset);
    final String indentInsideJavadoc = data.getIndentInsideJavadoc(helper, editor);

    originalHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);

    Project project = editor.getProject();
    if (indentInsideJavadoc != null &&
        project != null &&
        data.isLeadingAsteriskEnabled()) {
      if (editor instanceof EditorWindow) {
        editor = ((EditorWindow)editor).getDelegate();
      }
      editor.getDocument().insertString(editor.getCaretModel().getOffset(), "*" + indentInsideJavadoc);
    }

    helper.formatAtOffset(file, editor, editor.getCaretModel().getOffset(), EnterHandler.getLanguage(dataContext));
    return indentInsideJavadoc == null ? Result.Continue : Result.DefaultForceIndent;
  }

  private static final class Data {
    private final @NotNull PsiFile myFile;
    private final @NotNull Document myDocument;
    private final @NotNull CharSequence myText;
    private final int myOffset;

    private Data(@NotNull PsiFile file,
                 @NotNull Document document,
                 int offset) {
      final PsiElement element = file.findElementAt(offset);

      if (element != null) {
        final PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(element);
        if (injectionHost != null) {
          final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
          final Document hostDocument = documentManager.getDocument(injectionHost.getContainingFile());
          if (hostDocument != null) {
            myDocument = hostDocument;
            myText = hostDocument.getCharsSequence();
            myFile = injectionHost.getContainingFile();
            myOffset = injectionHost.getTextOffset();
            return;
          }
        }
      }

      myFile = file;
      myDocument = document;
      myText = document.getText();
      myOffset = offset;
    }

    public @Nullable String getIndentInsideJavadoc(EnterBetweenBracesDelegate helper, Editor editor) {
      final CodeDocumentationUtil.CommentContext commentContext = getCommentContext();
      return isInComment(helper, editor) && commentContext.docAsterisk
             ? CodeDocumentationUtil.getIndentInsideJavadoc(myDocument, myOffset)
             : null;
    }

    private @NotNull CodeDocumentationUtil.CommentContext getCommentContext() {
      final int line = myDocument.getLineNumber(myOffset);
      final int start = myDocument.getLineStartOffset(line);
      return CodeDocumentationUtil.tryParseCommentContext(myFile, myText, myOffset, start);
    }

    private boolean isInComment(final EnterBetweenBracesDelegate helper, Editor editor) {
      return helper.isInComment(myFile, editor, myOffset);
    }

    private boolean isLeadingAsteriskEnabled() {
      return CodeStyleManager.getInstance(myFile.getProject()).getDocCommentSettings(myFile).isLeadingAsteriskEnabled();
    }
  }

  protected boolean isApplicable(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 CharSequence documentText,
                                 int caretOffset,
                                 EnterBetweenBracesDelegate helper) {
    int prevCharOffset = CharArrayUtil.shiftBackward(documentText, caretOffset - 1, " \t");
    int nextCharOffset = CharArrayUtil.shiftForward(documentText, caretOffset, " \t");
    return isValidOffset(prevCharOffset, documentText) &&
           isValidOffset(nextCharOffset, documentText) &&
           helper.isBracePair(documentText.charAt(prevCharOffset), documentText.charAt(nextCharOffset)) &&
           !helper.bracesAreInTheSameElement(file, editor, prevCharOffset, nextCharOffset);
  }

  protected static @NotNull EnterBetweenBracesDelegate getLanguageImplementation(@Nullable Language language) {
    if (language != null) {
      final EnterBetweenBracesDelegate helper = EnterBetweenBracesDelegate.EP_NAME.forLanguage(language);
      if (helper != null) {
        return helper;
      }
    }
    return ourDefaultBetweenDelegate;
  }

  protected static EnterBetweenBracesDelegate ourDefaultBetweenDelegate = new EnterBetweenBracesDelegate();

  protected static boolean isValidOffset(int offset, CharSequence text) {
    return offset >= 0 && offset < text.length();
  }
}
