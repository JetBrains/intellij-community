/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.NontrivialBraceMatcher;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.*;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypedHandler extends TypedActionHandlerBase {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.TypedHandler");

  private static final Map<FileType,QuoteHandler> quoteHandlers = new HashMap<>();

  private static final Map<Class<? extends Language>, QuoteHandler> ourBaseLanguageQuoteHandlers = new HashMap<>();

  public TypedHandler(TypedActionHandler originalHandler){
    super(originalHandler);
  }

  @Nullable
  public static QuoteHandler getQuoteHandler(@NotNull PsiFile file, @NotNull Editor editor) {
    FileType fileType = getFileType(file, editor);
    QuoteHandler quoteHandler = getQuoteHandlerForType(fileType);
    if (quoteHandler == null) {
      FileType fileFileType = file.getFileType();
      if (fileFileType != fileType) {
        quoteHandler = getQuoteHandlerForType(fileFileType);
      }
    }
    if (quoteHandler == null) {
      return getLanguageQuoteHandler(file.getViewProvider().getBaseLanguage());
    }
    return quoteHandler;
  }

  public static QuoteHandler getLanguageQuoteHandler(Language baseLanguage) {
    for (Map.Entry<Class<? extends Language>, QuoteHandler> entry : ourBaseLanguageQuoteHandlers.entrySet()) {
      if (entry.getKey().isInstance(baseLanguage)) {
        return entry.getValue();
      }
    }
    return LanguageQuoteHandling.INSTANCE.forLanguage(baseLanguage);
  }

  private static FileType getFileType(@NotNull PsiFile file, @NotNull Editor editor) {
    FileType fileType = file.getFileType();
    Language language = PsiUtilBase.getLanguageInEditor(editor, file.getProject());
    if (language != null && language != PlainTextLanguage.INSTANCE) {
      LanguageFileType associatedFileType = language.getAssociatedFileType();
      if (associatedFileType != null) fileType = associatedFileType;
    }
    return fileType;
  }

  public static void registerBaseLanguageQuoteHandler(@NotNull Class<? extends Language> languageClass, @NotNull QuoteHandler quoteHandler) {
    ourBaseLanguageQuoteHandlers.put(languageClass, quoteHandler);
  }

  public static QuoteHandler getQuoteHandlerForType(@NotNull FileType fileType) {
    if (!quoteHandlers.containsKey(fileType)) {
      QuoteHandler handler = null;
      final QuoteHandlerEP[] handlerEPs = Extensions.getExtensions(QuoteHandlerEP.EP_NAME);
      for(QuoteHandlerEP ep: handlerEPs) {
        if (ep.fileType.equals(fileType.getName())) {
          handler = ep.getHandler();
          break;
        }
      }
      quoteHandlers.put(fileType, handler);
    }
    return quoteHandlers.get(fileType);
  }

  /** @see QuoteHandlerEP */
  @Deprecated
  public static void registerQuoteHandler(@NotNull FileType fileType, @NotNull QuoteHandler quoteHandler) {
    quoteHandlers.put(fileType, quoteHandler);
  }

  @Override
  public void execute(@NotNull final Editor originalEditor, final char charTyped, @NotNull final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final PsiFile originalFile;

    if (project == null || (originalFile = PsiUtilBase.getPsiFileInEditor(originalEditor, project)) == null) {
      if (myOriginalHandler != null){
        myOriginalHandler.execute(originalEditor, charTyped, dataContext);
      }
      return;
    }

    if (!CodeInsightUtilBase.prepareEditorForWrite(originalEditor)) return;

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    final Document originalDocument = originalEditor.getDocument();
    originalEditor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        if (psiDocumentManager.isDocumentBlockedByPsi(originalDocument)) {
          psiDocumentManager.doPostponedOperationsAndUnblockDocument(originalDocument); // to clean up after previous caret processing
        }

        Editor editor = injectedEditorIfCharTypedIsSignificant(charTyped, originalEditor, originalFile);
        PsiFile file = editor == originalEditor ? originalFile : psiDocumentManager.getPsiFile(editor.getDocument());


        final TypedHandlerDelegate[] delegates = Extensions.getExtensions(TypedHandlerDelegate.EP_NAME);

        boolean handled = false;
        for (TypedHandlerDelegate delegate : delegates) {
          final TypedHandlerDelegate.Result result = delegate.checkAutoPopup(charTyped, project, editor, file);
          handled = result == TypedHandlerDelegate.Result.STOP;
          if (result != TypedHandlerDelegate.Result.CONTINUE) {
            break;
          }
        }

        if (!handled) {
          autoPopupCompletion(editor, charTyped, project, file);
          autoPopupParameterInfo(editor, charTyped, project, file);
        }

        if (!editor.isInsertMode()) {
          type(originalEditor, charTyped);
          return;
        }

        EditorModificationUtil.deleteSelectedText(editor);

        FileType fileType = getFileType(file, editor);

        for (TypedHandlerDelegate delegate : delegates) {
          final TypedHandlerDelegate.Result result = delegate.beforeCharTyped(charTyped, project, editor, file, fileType);
          if (result == TypedHandlerDelegate.Result.STOP) {
            return;
          }
          if (result == TypedHandlerDelegate.Result.DEFAULT) {
            break;
          }
        }

        if (')' == charTyped || ']' == charTyped || '}' == charTyped) {
          if (FileTypes.PLAIN_TEXT != fileType) {
            if (handleRParen(editor, fileType, charTyped)) return;
          }
        }
        else if ('"' == charTyped || '\'' == charTyped || '`' == charTyped/* || '/' == charTyped*/) {
          if (handleQuote(editor, charTyped, file)) return;
        }

        long modificationStampBeforeTyping = editor.getDocument().getModificationStamp();
        type(originalEditor, charTyped);
        AutoHardWrapHandler.getInstance().wrapLineIfNecessary(originalEditor, dataContext, modificationStampBeforeTyping);

        if (('(' == charTyped || '[' == charTyped || '{' == charTyped) &&
            CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
            fileType != FileTypes.PLAIN_TEXT) {
          handleAfterLParen(editor, fileType, charTyped);
        }
        else if ('}' == charTyped) {
          indentClosingBrace(project, editor);
        }
        else if (')' == charTyped) {
          indentClosingParenth(project, editor);
        }

        for (TypedHandlerDelegate delegate : delegates) {
          final TypedHandlerDelegate.Result result = delegate.charTyped(charTyped, project, editor, file);
          if (result == TypedHandlerDelegate.Result.STOP) {
            return;
          }
          if (result == TypedHandlerDelegate.Result.DEFAULT) {
            break;
          }
        }
        if ('{' == charTyped) {
          indentOpenedBrace(project, editor);
        }
        else if ('(' == charTyped) {
          indentOpenedParenth(project, editor);
        }
      }
    });
  }

  private static void type(Editor editor, char charTyped) {
    CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
    EditorModificationUtil.insertStringAtCaret(editor, String.valueOf(charTyped), true, true);
  }

  private static void autoPopupParameterInfo(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    if ((charTyped == '(' || charTyped == ',') && !isInsideStringLiteral(editor, file)) {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }
  }

  public static void autoPopupCompletion(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    if (charTyped == '.' || isAutoPopup(editor, file, charTyped)) {
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
    }
  }
  
  public static void commitDocumentIfCurrentCaretIsNotTheFirstOne(@NotNull Editor editor, @NotNull Project project) {
    if (ContainerUtil.getFirstItem(editor.getCaretModel().getAllCarets()) != editor.getCaretModel().getCurrentCaret()) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
  }

  private static boolean isAutoPopup(@NotNull Editor editor, @NotNull PsiFile file, char charTyped) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    if (offset >= 0) {
      final PsiElement element = file.findElementAt(offset);
      if (element != null) {
        final List<CompletionContributor> list = CompletionContributor.forLanguage(element.getLanguage());
        for (CompletionContributor contributor : list) {
          if (contributor.invokeAutoPopup(element, charTyped)) return true;
        }
      }
    }
    return false;
  }

  private static boolean isInsideStringLiteral(@NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
    if (definition != null) {
      final TokenSet stringLiteralElements = definition.getStringLiteralElements();
      final ASTNode node = element.getNode();
      if (node == null) return false;
      final IElementType elementType = node.getElementType();
      if (stringLiteralElements.contains(elementType)) {
        return true;
      }
      PsiElement parent = element.getParent();
      if (parent != null) {
        ASTNode parentNode = parent.getNode();
        if (parentNode != null && stringLiteralElements.contains(parentNode.getElementType())) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static Editor injectedEditorIfCharTypedIsSignificant(final char charTyped, @NotNull Editor editor, @NotNull PsiFile oldFile) {
    int offset = editor.getCaretModel().getOffset();
    // even for uncommitted document try to retrieve injected fragment that has been there recently
    // we are assuming here that when user is (even furiously) typing, injected language would not change
    // and thus we can use its lexer to insert closing braces etc
    for (DocumentWindow documentWindow : InjectedLanguageUtil.getCachedInjectedDocuments(oldFile)) {
      if (documentWindow.isValid() && documentWindow.containsRange(offset, offset)) {
        PsiFile injectedFile = PsiDocumentManager.getInstance(oldFile.getProject()).getPsiFile(documentWindow);
        if (injectedFile != null) {
          Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
          // IDEA-52375/WEB-9105 fix: last quote in editable fragment should be handled by outer language quote handler
          TextRange hostRange = documentWindow.getHostRange(offset);
          CharSequence sequence = editor.getDocument().getCharsSequence();
          if (sequence.length() > offset && charTyped != sequence.charAt(offset) || hostRange != null && hostRange.contains(offset)) {
            return injectedEditor;
          }
        }
      }
    }

    return editor;
  }

  private static void handleAfterLParen(@NotNull Editor editor, @NotNull FileType fileType, char lparenChar){
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    boolean atEndOfDocument = offset == editor.getDocument().getTextLength();

    if (!atEndOfDocument) iterator.retreat();
    if (iterator.atEnd()) return;
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    if (iterator.atEnd()) return;
    IElementType braceTokenType = iterator.getTokenType();
    final CharSequence fileText = editor.getDocument().getCharsSequence();
    if (!braceMatcher.isLBraceToken(iterator, fileText, fileType)) return;

    if (!iterator.atEnd()) {
      iterator.advance();

      if (!iterator.atEnd() && 
          !BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType(braceTokenType, iterator.getTokenType(), fileType)) {
        return;
      }

      iterator.retreat();
    }

    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, fileText,fileType);
    if (lparenOffset < 0) lparenOffset = 0;

    iterator = ((EditorEx)editor).getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(fileText, fileType, iterator, true, true);

    if (!matched) {
      String text;
      if (lparenChar == '(') {
        text = ")";
      }
      else if (lparenChar == '[') {
        text = "]";
      }
      else if (lparenChar == '<') {
        text = ">";
      }
      else if (lparenChar == '{') {
        text = "}";
      }
      else {
        throw new AssertionError("Unknown char "+lparenChar);
      }
      editor.getDocument().insertString(offset, text);
    }
  }

  public static boolean handleRParen(@NotNull Editor editor, @NotNull FileType fileType, char charTyped) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;

    if (iterator.getEnd() - iterator.getStart() != 1 || editor.getDocument().getCharsSequence().charAt(iterator.getStart()) != charTyped) {
      return false;
    }

    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    CharSequence text = editor.getDocument().getCharsSequence();
    if (!braceMatcher.isRBraceToken(iterator, text, fileType)) {
      return false;
    }

    IElementType tokenType = iterator.getTokenType();

    iterator.retreat();

    IElementType lparenTokenType = braceMatcher.getOppositeBraceTokenType(tokenType);
    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(
      iterator,
      lparenTokenType,
      text,
      fileType
    );

    if (lparenthOffset < 0) {
      if (braceMatcher instanceof NontrivialBraceMatcher) {
        for(IElementType t:((NontrivialBraceMatcher)braceMatcher).getOppositeBraceTokenTypes(tokenType)) {
          if (t == lparenTokenType) continue;
          lparenthOffset = BraceMatchingUtil.findLeftmostLParen(
            iterator,
            t, text,
            fileType
          );
          if (lparenthOffset >= 0) break;
        }
      }
      if (lparenthOffset < 0) return false;
    }

    iterator = ((EditorEx) editor).getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(text, fileType, iterator, true, true);

    if (!matched) return false;

    EditorModificationUtil.moveCaretRelatively(editor, 1);
    return true;
  }

  private static boolean handleQuote(@NotNull Editor editor, char quote, @NotNull PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return false;
    final QuoteHandler quoteHandler = getQuoteHandler(file, editor);
    if (quoteHandler == null) return false;

    int offset = editor.getCaretModel().getOffset();

    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    int length = document.getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) return false;

    if (offset < length && chars.charAt(offset) == quote){
      if (isClosingQuote(editor, quoteHandler, offset)){
        EditorModificationUtil.moveCaretRelatively(editor, 1);
        return true;
      }
    }

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    if (!iterator.atEnd()){
      IElementType tokenType = iterator.getTokenType();
      if (quoteHandler instanceof JavaLikeQuoteHandler) {
        try {
          if (!((JavaLikeQuoteHandler)quoteHandler).isAppropriateElementTypeForLiteral(tokenType)) return false;
        }
        catch (AbstractMethodError incompatiblePluginErrorThatDoesNotInterestUs) {
          // ignore
        }
      }
    }

    type(editor, quote);
    offset = editor.getCaretModel().getOffset();

    if (quoteHandler instanceof MultiCharQuoteHandler) {
      CharSequence closingQuote = getClosingQuote(editor, (MultiCharQuoteHandler)quoteHandler, offset);
      if (closingQuote != null && hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
        if (offset == document.getTextLength() ||
            !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
          document.insertString(offset, closingQuote);
          return true;
        }
      }
    }

    if (isOpeningQuote(editor, quoteHandler, offset - 1) && hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      if (offset == document.getTextLength() ||
          !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
        document.insertString(offset, String.valueOf(quote));
      }
    }

    return true;
  }

  private static boolean isClosingQuote(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isClosingQuote(iterator,offset);
  }

  @Nullable
  private static CharSequence getClosingQuote(@NotNull Editor editor, @NotNull MultiCharQuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return null;
    }

    return quoteHandler.getClosingQuote(iterator, offset);
  }

  private static boolean isOpeningQuote(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private static boolean hasNonClosedLiterals(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private static boolean isTypingEscapeQuote(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = offset - 1 - offset1;
    return slashCount % 2 != 0 && isInsideLiteral(editor, quoteHandler, offset);
  }

  private static boolean isInsideLiteral(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset - 1);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isInsideLiteral(iterator);
  }

  private static void indentClosingBrace(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '}');
  }

  static void indentOpenedBrace(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '{');
  }

  private static void indentOpenedParenth(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '(');
  }

  private static void indentClosingParenth(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, ')');
  }

  private static void indentBrace(@NotNull final Project project, @NotNull final Editor editor, final char braceChar) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != braceChar) return;

    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitDocument(document);

      final PsiFile file = documentManager.getPsiFile(document);
      if (file == null || !file.isWritable()) return;
      PsiElement element = file.findElementAt(offset);
      if (element == null) return;

      EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);

      final FileType fileType = file.getFileType();
      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
      boolean rBraceToken = braceMatcher.isRBraceToken(iterator, chars, fileType);
      final boolean isBrace = braceMatcher.isLBraceToken(iterator, chars, fileType) || rBraceToken;
      int lBraceOffset = -1;

      if (CodeInsightSettings.getInstance().REFORMAT_BLOCK_ON_RBRACE &&
          rBraceToken &&
          braceMatcher.isStructuralBrace(iterator, chars, fileType) && offset > 0) {
        lBraceOffset = BraceMatchingUtil.findLeftLParen(
          highlighter.createIterator(offset - 1),
          braceMatcher.getOppositeBraceTokenType(iterator.getTokenType()),
          editor.getDocument().getCharsSequence(),
          fileType
        );
      }
      if (element.getNode() != null && isBrace) {
        final int finalLBraceOffset = lBraceOffset;
        ApplicationManager.getApplication().runWriteAction(() -> {
          try{
            int newOffset;
            if (finalLBraceOffset != -1) {
              RangeMarker marker = document.createRangeMarker(offset, offset + 1);
              CodeStyleManager.getInstance(project).reformatRange(file, finalLBraceOffset, offset, true);
              newOffset = marker.getStartOffset();
              marker.dispose();
            } else {
              newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
            }

            editor.getCaretModel().moveToOffset(newOffset + 1);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            editor.getSelectionModel().removeSelection();
          }
          catch(IncorrectOperationException e){
            LOG.error(e);
          }
        });
      }
    }
  }

}

