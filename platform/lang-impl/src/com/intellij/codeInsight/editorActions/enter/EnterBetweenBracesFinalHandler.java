// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.formatting.IndentInfo;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Please, don't extend the class.
 * Use the {@code EnterBetweenBracesDelegate} language-specific implementation instead.
 */
public class EnterBetweenBracesFinalHandler implements EnterHandlerDelegate {
  private static final Key<InjectedIndentState> INJECTED_INDENT_STATE = Key.create("EnterBetweenBracesFinalHandler.injectedIndentState");

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
    final InjectedIndentState state = saveInjectedIndentState(file, document, caretOffset, dataContext);
    if (state != null) {
      // The formatter of the injected language does not run, so remove the space before the closing brace here.
      int rightBraceOffset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
      document.deleteString(caretOffset, rightBraceOffset);
    }

    originalHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);

    Project project = editor.getProject();
    if (indentInsideJavadoc != null &&
        project != null &&
        data.isLeadingAsteriskEnabled()) {
      editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
      editor.getDocument().insertString(editor.getCaretModel().getOffset(), "*" + indentInsideJavadoc);
    }

    if (state == null) {
      helper.formatAtOffset(file, editor, editor.getCaretModel().getOffset(), EnterHandler.getLanguage(dataContext));
    }
    return indentInsideJavadoc == null ? Result.Continue : Result.DefaultForceIndent;
  }

  /**
   * Indents the new line of an injected fragment, and restores the host line prefix.
   * {@link EnterBetweenBracesFinalHandler} and {@link EnterAfterUnmatchedBraceHandler} do not format such a fragment. The formatter of
   * the host language does not know the structure of the fragment, and the formatter of the injected language does not know the host
   * line prefix. This handler runs last, after the default indent adjustment.
   */
  static final class InjectedIndentPostProcessor implements EnterHandlerDelegate {
    @Override
    public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
      if (!(dataContext instanceof UserDataHolder context)) {
        return Result.Continue;
      }

      InjectedIndentState state = context.getUserData(INJECTED_INDENT_STATE);
      context.putUserData(INJECTED_INDENT_STATE, null);
      Project project = editor.getProject();
      if (state == null || project == null) {
        return Result.Continue;
      }

      // The injected fragment can be invalid now, so change the host document only. Each line of the fragment starts at a host line,
      // right after the host line prefix, so a host line and a line of the fragment share the indent.
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
      Document hostDocument = hostEditor.getDocument();
      restoreHostLinePrefixes(hostEditor, state.hostLinePrefix);
      documentManager.commitDocument(hostDocument);

      String codeIndent = state.getNextCodeIndent(getIndentEditor(documentManager, hostEditor, state));
      int caretLine = hostDocument.getLineNumber(hostEditor.getCaretModel().getOffset());
      replaceLineIndent(hostDocument, caretLine + 1, state, state.codeIndent);
      replaceLineIndent(hostDocument, caretLine, state, codeIndent);
      hostEditor.getCaretModel().moveToOffset(hostDocument.getLineEndOffset(caretLine));
      documentManager.commitDocument(hostDocument);
      return Result.Continue;
    }
  }

  /**
   * Saves the indent of an injected fragment that the formatter cannot format.
   * {@link InjectedIndentPostProcessor} indents the fragment after the Enter action.
   *
   * @return the saved state, or {@code null} if the caller must format the fragment
   */
  static @Nullable InjectedIndentState saveInjectedIndentState(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               int offset,
                                                               @NotNull DataContext dataContext) {
    if (!(dataContext instanceof UserDataHolder context)) {
      return null;
    }

    String hostLinePrefix = getRepeatedHostLinePrefix(file, document, offset);
    if (hostLinePrefix == null) {
      return null;
    }
    CommonCodeStyleSettings.IndentOptions languageIndentOptions =
      CodeStyle.getSettings(file.getProject()).getCommonSettings(file.getLanguage()).getIndentOptions();
    CommonCodeStyleSettings.IndentOptions indentOptions =
      languageIndentOptions != null ? languageIndentOptions : CodeStyle.getIndentOptions(file);
    int injectedAnchor = Math.max(0, CharArrayUtil.shiftBackward(document.getCharsSequence(), offset - 1, " \t"));
    int hostAnchor = document instanceof DocumentWindow documentWindow ? documentWindow.injectedToHost(injectedAnchor) : injectedAnchor;
    InjectedIndentState state =
      new InjectedIndentState(file.getLanguage(), hostLinePrefix, getLineIndent(document, offset), indentOptions, hostAnchor);
    context.putUserData(INJECTED_INDENT_STATE, state);
    return state;
  }

  /**
   * Returns the editor that can tell the indent of the injected language, or {@code null} if no editor can tell it.
   * The Enter action replaces the editor of an injected fragment, so search the editor of the fragment again.
   * <p>
   * The host editor cannot tell the indent of a fragment that has a host line prefix. A host line holds the prefix, so the indent of
   * the host line counts the prefix, and {@link #replaceLineIndent} adds the prefix again. Without a prefix a line of the fragment and
   * a host line are the same line, so the host editor is a good stand-in when the search fails.
   */
  private static @Nullable Editor getIndentEditor(@NotNull PsiDocumentManager documentManager,
                                                  @NotNull Editor hostEditor,
                                                  @NotNull InjectedIndentState state) {
    PsiFile hostFile = documentManager.getPsiFile(hostEditor.getDocument());
    PsiElement injectedElement = hostFile == null
                                 ? null
                                 : InjectedLanguageManager.getInstance(hostFile.getProject())
                                   .findInjectedElementAt(hostFile, state.hostAnchor);
    if (injectedElement != null) {
      PsiFile injectedFile = PsiUtilCore.getTemplateLanguageFile(injectedElement.getContainingFile());
      Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile);
      if (injectedEditor instanceof EditorWindow editorWindow && editorWindow.isValid()) {
        return editorWindow;
      }
    }
    return state.hostLinePrefix.isEmpty() ? hostEditor : null;
  }

  /**
   * Checks if the formatter can format the injected fragment at the offset.
   * The answer is no for every fragment that keeps a whole host line. The formatter of the host language does not know the structure of
   * such a fragment, and the formatter of the injected language does not know the host line prefix.
   * <p>
   * The check reads the host line of the fragment only, so the answer also holds after the caller changed the document.
   */
  static boolean isFormattableInjectedFragment(@NotNull PsiFile file, @NotNull Document document, int offset) {
    return getHostLinePrefix(file, document, offset) == null;
  }

  /**
   * Returns the prefix that the host adds to each line of an injected fragment, or {@code null} if the host adds no such prefix.
   * An indented code fence adds an indent, and a block comment adds a leading asterisk.
   * <p>
   * The prefix is empty when the fragment starts at a host line. Then a line of the fragment and a host line are the same line.
   * <p>
   * The method reads the next line of the fragment, so call it before the change of the document. A new line of the fragment has no
   * prefix yet, and {@link InjectedIndentPostProcessor} adds the prefix later.
   */
  private static @Nullable String getRepeatedHostLinePrefix(@NotNull PsiFile file, @NotNull Document document, int offset) {
    String prefix = getHostLinePrefix(file, document, offset);
    if (prefix == null || prefix.isEmpty() || !(document instanceof DocumentWindow documentWindow)) {
      return prefix;
    }
    return startsNextFragmentLine(documentWindow, document.getLineNumber(offset) + 1, prefix) ? prefix : null;
  }

  /**
   * Returns the host text that goes before an injected fragment on the host line of the fragment, or {@code null} if the fragment does
   * not keep the rest of that host line. A string literal gives {@code null}, because the host line goes on after the literal.
   */
  private static @Nullable String getHostLinePrefix(@NotNull PsiFile file, @NotNull Document document, int offset) {
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
    if (!injectedLanguageManager.isInjectedFragment(file) || !(document instanceof DocumentWindow documentWindow)) {
      return null;
    }

    int injectedLine = document.getLineNumber(offset);
    int hostContentStart = documentWindow.injectedToHost(document.getLineStartOffset(injectedLine));
    int hostContentEnd = documentWindow.injectedToHost(document.getLineEndOffset(injectedLine));
    Document hostDocument = documentWindow.getDelegate();
    int hostLine = hostDocument.getLineNumber(hostContentStart);
    if (hostContentEnd < hostDocument.getLineEndOffset(hostLine)) {
      // The host line goes on after the fragment, as with a string literal. Then the text before the fragment is not a line prefix.
      return null;
    }
    return hostDocument.getCharsSequence().subSequence(hostDocument.getLineStartOffset(hostLine), hostContentStart).toString();
  }

  /**
   * Checks if the host also adds the prefix to the given line of an injected fragment.
   * A fragment that ends before the given line needs no check, because the host adds nothing after the fragment.
   * <p>
   * The check tells a line prefix from the text of one line. A tag before the code of a script is text of one line. The host accepts a
   * bare line break after the tag, so the next line of the fragment does not start with the tag.
   */
  private static boolean startsNextFragmentLine(@NotNull DocumentWindow documentWindow, int injectedLine, @NotNull String prefix) {
    if (injectedLine >= documentWindow.getLineCount()) {
      return true;
    }

    Document hostDocument = documentWindow.getDelegate();
    int hostStart = documentWindow.injectedToHost(documentWindow.getLineStartOffset(injectedLine));
    int hostLineStart = hostDocument.getLineStartOffset(hostDocument.getLineNumber(hostStart));
    return CharArrayUtil.regionMatches(hostDocument.getCharsSequence(), hostLineStart, prefix);
  }

  private static @NotNull String getLineIndent(@NotNull Document document, int offset) {
    int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
    int contentStart = CharArrayUtil.shiftForward(document.getCharsSequence(), lineStart, " \t");
    return document.getCharsSequence().subSequence(lineStart, contentStart).toString();
  }

  /**
   * Adds the host line prefix to the new line of the caret and to the new line of the closing brace.
   */
  private static void restoreHostLinePrefixes(@NotNull Editor hostEditor, @NotNull String prefix) {
    if (prefix.isEmpty()) {
      return;
    }

    Document hostDocument = hostEditor.getDocument();
    int caretOffset = hostEditor.getCaretModel().getOffset();
    int caretLine = hostDocument.getLineNumber(caretOffset);
    addLinePrefix(hostDocument, caretLine + 1, prefix);
    if (addLinePrefix(hostDocument, caretLine, prefix)) {
      hostEditor.getCaretModel().moveToOffset(caretOffset + prefix.length());
    }
  }

  private static boolean addLinePrefix(@NotNull Document document, int line, @NotNull String prefix) {
    if (line >= document.getLineCount()) {
      return false;
    }

    int lineStart = document.getLineStartOffset(line);
    CharSequence text = document.getCharsSequence();
    if (lineStart + prefix.length() <= text.length() && CharArrayUtil.regionMatches(text, lineStart, prefix)) {
      return false;
    }

    document.insertString(lineStart, prefix);
    return true;
  }

  private static void replaceLineIndent(@NotNull Document document,
                                        int line,
                                        @NotNull InjectedIndentState state,
                                        @NotNull String codeIndent) {
    if (line >= document.getLineCount()) {
      return;
    }

    int lineStart = document.getLineStartOffset(line);
    int lineEnd = document.getLineEndOffset(line);
    int contentStart = lineStart;
    CharSequence text = document.getCharsSequence();
    if (lineStart + state.hostLinePrefix.length() <= lineEnd &&
        CharArrayUtil.regionMatches(text, lineStart, state.hostLinePrefix)) {
      contentStart += state.hostLinePrefix.length();
    }
    contentStart = CharArrayUtil.shiftForward(text, contentStart, " \t");
    if (contentStart > lineEnd) {
      contentStart = lineEnd;
    }
    document.replaceString(lineStart, contentStart, state.hostLinePrefix + codeIndent);
  }

  static final class InjectedIndentState {
    private final @NotNull Language language;
    private final @NotNull String hostLinePrefix;
    private final @NotNull String codeIndent;
    private final @NotNull CommonCodeStyleSettings.IndentOptions indentOptions;
    private final int hostAnchor;

    private InjectedIndentState(@NotNull Language language,
                                @NotNull String hostLinePrefix,
                                @NotNull String codeIndent,
                                @NotNull CommonCodeStyleSettings.IndentOptions indentOptions,
                                int hostAnchor) {
      this.language = language;
      this.hostLinePrefix = hostLinePrefix;
      this.codeIndent = codeIndent;
      // The Enter action can invalidate the injected file, so keep a copy of the options of the injected language.
      this.indentOptions = (CommonCodeStyleSettings.IndentOptions)indentOptions.clone();
      this.hostAnchor = hostAnchor;
    }

    /**
     * Returns the indent of the new line between the braces. It is the indent of the injected language when that language provides one.
     * Otherwise, it is the indent of the opening brace plus one indent step.
     */
    private @NotNull String getNextCodeIndent(@Nullable Editor indentEditor) {
      String indent = indentEditor == null
                      ? null
                      : CodeStyle.getLineIndent(indentEditor, language, indentEditor.getCaretModel().getOffset(), false);
      int codeColumns = getIndentColumns(codeIndent, indentOptions.TAB_SIZE);
      int columns = indent == null ? -1 : getIndentColumns(indent, indentOptions.TAB_SIZE);
      // The line between the braces is always deeper than the line of the opening brace. A smaller indent means that the injected
      // language cannot indent the fragment, because the editor of the fragment keeps the syntax of the host.
      if (columns <= codeColumns) {
        return codeIndent + new IndentInfo(0, indentOptions.INDENT_SIZE, 0).generateNewWhiteSpace(indentOptions);
      }
      // The injected language can indent with another character, so generate the indent again with the options of the fragment.
      return new IndentInfo(0, columns, 0).generateNewWhiteSpace(indentOptions);
    }

    private static int getIndentColumns(@NotNull String indent, int tabSize) {
      int columns = 0;
      for (int i = 0; i < indent.length(); i++) {
        columns = indent.charAt(i) == '\t' ? columns + tabSize - columns % tabSize : columns + 1;
      }
      return columns;
    }
  }

  private static final class Data {
    private final @NotNull PsiFile myPsiFile;
    private final @NotNull Document myDocument;
    private final @NotNull CharSequence myText;
    private final int myOffset;

    private Data(@NotNull PsiFile psiFile,
                 @NotNull Document document,
                 int offset) {
      final PsiElement element = psiFile.findElementAt(offset);

      if (element != null) {
        final PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(psiFile.getProject()).getInjectionHost(element);
        if (injectionHost != null) {
          final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
          final Document hostDocument = documentManager.getDocument(injectionHost.getContainingFile());
          if (hostDocument != null) {
            myDocument = hostDocument;
            myText = hostDocument.getCharsSequence();
            myPsiFile = injectionHost.getContainingFile();
            myOffset = injectionHost.getTextOffset();
            return;
          }
        }
      }

      myPsiFile = psiFile;
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
      return CodeDocumentationUtil.tryParseCommentContext(myPsiFile, myText, myOffset, start);
    }

    private boolean isInComment(final EnterBetweenBracesDelegate helper, Editor editor) {
      return helper.isInComment(myPsiFile, editor, myOffset);
    }

    private boolean isLeadingAsteriskEnabled() {
      return CodeStyleManager.getInstance(myPsiFile.getProject()).getDocCommentSettings(myPsiFile).isLeadingAsteriskEnabled();
    }
  }

  protected boolean isApplicable(@NotNull PsiFile psiFile,
                                 @NotNull Editor editor,
                                 CharSequence documentText,
                                 int caretOffset,
                                 EnterBetweenBracesDelegate helper) {
    int prevCharOffset = CharArrayUtil.shiftBackward(documentText, caretOffset - 1, " \t");
    int nextCharOffset = CharArrayUtil.shiftForward(documentText, caretOffset, " \t");
    return isValidOffset(prevCharOffset, documentText) &&
           isValidOffset(nextCharOffset, documentText) &&
           helper.isBracePair(documentText.charAt(prevCharOffset), documentText.charAt(nextCharOffset)) &&
           !helper.bracesAreInTheSameElement(psiFile, editor, prevCharOffset, nextCharOffset);
  }

  @ApiStatus.Internal
  protected static @NotNull EnterBetweenBracesDelegate getLanguageImplementation(@Nullable Language language) {
    if (language != null) {
      final EnterBetweenBracesDelegate helper = EnterBetweenBracesDelegate.EP_NAME.forLanguage(language);
      if (helper != null) {
        return helper;
      }
    }
    return ourDefaultBetweenDelegate;
  }

  @ApiStatus.Internal
  public static boolean isBracePair(@Nullable Language language, char leftBrace, char rightBrace) {
    return getLanguageImplementation(language).isBracePair(leftBrace, rightBrace);
  }

  @ApiStatus.Internal
  protected static EnterBetweenBracesDelegate ourDefaultBetweenDelegate = new EnterBetweenBracesDelegate();

  @ApiStatus.Internal
  protected static boolean isValidOffset(int offset, CharSequence text) {
    return offset >= 0 && offset < text.length();
  }
}
