package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

import java.util.HashMap;
import java.util.Map;

public class TypedHandler implements TypedActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.TypedHandler");

  private TypedActionHandler myOriginalHandler;

  public interface JavaLikeQuoteHandler extends QuoteHandler {
    TokenSet getConcatenatableStringTokenTypes();
    String getStringConcatenationOperatorRepresentation();

    TokenSet getStringTokenTypes();
  }

  public interface QuoteHandler {
    boolean isClosingQuote(HighlighterIterator iterator, int offset);
    boolean isOpeningQuote(HighlighterIterator iterator, int offset);
    boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset);
    boolean isInsideLiteral(HighlighterIterator iterator);
  }

  private static final Map<FileType,QuoteHandler> quoteHandlers = new HashMap<FileType, QuoteHandler>();

  public static QuoteHandler getQuoteHandler(FileType fileType) {
    return quoteHandlers.get(fileType);
  }

  public static void registerQuoteHandler(FileType fileType, QuoteHandler quoteHandler) {
    quoteHandlers.put(fileType, quoteHandler);
  }

  public static class SimpleTokenSetQuoteHandler implements QuoteHandler {
    protected final TokenSet myLiteralTokenSet;

    public SimpleTokenSetQuoteHandler(IElementType[] _literalTokens) {
      myLiteralTokenSet = TokenSet.create(_literalTokens);
    }

    public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
      final IElementType tokenType = iterator.getTokenType();

      if (myLiteralTokenSet.contains(tokenType)){
        int start = iterator.getStart();
        int end = iterator.getEnd();
        return end - start >= 1 && offset == end - 1;
      }

      return false;
    }

    public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
      if (myLiteralTokenSet.contains(iterator.getTokenType())){
        int start = iterator.getStart();
        return offset == start;
      }

      return false;
    }

    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
      try {
        Document doc = editor.getDocument();
        CharSequence chars = doc.getCharsSequence();
        int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));

        while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
          IElementType tokenType = iterator.getTokenType();

          if (myLiteralTokenSet.contains(tokenType)) {
            if (iterator.getStart() >= iterator.getEnd() - 1 ||
                chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
              return true;
            }
          }
          iterator.advance();
        }
      }
      finally {
        while(iterator.atEnd() || iterator.getStart() != offset) iterator.retreat();
      }

      return false;
    }

    public boolean isInsideLiteral(HighlighterIterator iterator) {
      return myLiteralTokenSet.contains(iterator.getTokenType());
    }
  }

  public TypedHandler(TypedActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, char charTyped, DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null || editor.isColumnMode()){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    if (editor.isViewer()) return;

    if (!editor.getDocument().isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)) {
        return;
      }
    }

    AutoPopupController autoPopupController = AutoPopupController.getInstance(project);

    if (charTyped == '.') {
      autoPopupController.autoPopupMemberLookup(editor);
    }

    if (charTyped == '#') {
      autoPopupController.autoPopupMemberLookup(editor);
    }

    if (charTyped == '@' && file instanceof PsiJavaFile) {
      autoPopupController.autoPopupJavadocLookup(editor);
    }

    final boolean isXmlLikeFile = file.getViewProvider().getBaseLanguage() instanceof XMLLanguage;
    boolean spaceInTag = isXmlLikeFile && charTyped == ' ';

    if (spaceInTag) {
      spaceInTag = false;
      final PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
      
      if (at != null) {
        final PsiElement parent = at.getParent();
        if (parent instanceof XmlTag) {
          spaceInTag = true;
        }
      }
    }

    if ((charTyped == '<' || charTyped == '{' || charTyped == '/' || spaceInTag) && isXmlLikeFile) {
      autoPopupController.autoPopupXmlLookup(editor);
    }

    if (charTyped == '('){
      autoPopupController.autoPopupParameterInfo(editor, null);
    }

    if (!editor.isInsertMode()){
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    if (editor.getSelectionModel().hasSelection()){
      EditorModificationUtil.deleteSelectedText(editor);
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    FileType fileType;
    FileType originalFileType = null;

    if (virtualFile != null){
      originalFileType = fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
    }
    else {
      fileType = file.getFileType();
    }

    if ('>' == charTyped){
      if (file instanceof XmlFile){
        if(handleXmlGreater(project, editor, fileType)) return;
      }
      else if (file instanceof PsiJavaFile && !(file instanceof JspFile) &&
          CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
               ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
        if (handleJavaGT(editor)) return;
      }
    }
    else if (')' == charTyped){
      if (handleRParen(editor, fileType, ')', '(')) return;
    }
    else if (']' == charTyped){
      if (handleRParen(editor, fileType, ']', '[')) return;
    }
    else if (';' == charTyped) {
      if (handleSemicolon(editor, fileType)) return;
    }
    else if ('"' == charTyped || '\'' == charTyped){
      if (handleQuote(editor, fileType, charTyped, dataContext)) return;
    } else if ('}' == charTyped) {
      if (originalFileType == StdFileTypes.JSPX || originalFileType == StdFileTypes.JSP) {
        if (handleELClosingBrace(editor, file, project)) return;
      } else if (originalFileType == StdFileTypes.JAVA) {
        if (handleJavaArrayInitializerRBrace(editor)) return;
      }
    } else if ('/' == charTyped){
      if (file instanceof XmlFile){
        if(handleXmlSlashInEmptyEnd(project, editor)) return;
      }
    }

    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    boolean handleAfterJavaLT = '<' == charTyped &&
                                file instanceof PsiJavaFile &&
                                !(file instanceof JspFile) &&
                                CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                                ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0 &&
                                BraceMatchingUtil.isAfterClassLikeIdentifierOrDot(offsetBefore, editor);

    myOriginalHandler.execute(editor, charTyped, dataContext);

    if (handleAfterJavaLT) {
      handleAfterJavaLT(editor);
    }
    else if ('(' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '(');
    }
    else if ('[' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '[');
    }
    else if ('}' == charTyped){
      indentClosingBrace(project, editor);
    }
    else if ('{' == charTyped){
      if (originalFileType == StdFileTypes.JSPX ||
          originalFileType == StdFileTypes.JSP
        ) {
        if(handleELOpeningBrace(editor, file, project)) return;
      } else if (originalFileType == StdFileTypes.JAVA) {
        if (handleJavaArrayInitializerLBrace(editor)) return;
      }

      indentOpenedBrace(project, editor);
    }
    else if ('/' == charTyped){
      if (file instanceof XmlFile){
        handleXmlSlash(project, editor);
      }
    } else if ('=' == charTyped) {
      if (originalFileType == StdFileTypes.JSP) {
        handleJspEqual(project, editor);
      }
    }
  }

  private boolean handleJavaArrayInitializerRBrace(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getStart() == 0 || iterator.getTokenType() != JavaTokenType.RBRACE) return false;
    iterator.retreat();
    if (!checkArrayInitializerLBrace(iterator)) return false;
    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private boolean handleJavaArrayInitializerLBrace(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset - 1);
    if (!checkArrayInitializerLBrace(iterator)) return false;
    editor.getDocument().insertString(offset, "}");
    return true;
  }

  private boolean checkArrayInitializerLBrace(final HighlighterIterator iterator) {
    if (iterator.getTokenType() != JavaTokenType.LBRACE) return false;
    iterator.retreat();
    if (iterator.getTokenType() == JavaTokenType.WHITE_SPACE) iterator.retreat();
    if (iterator.getTokenType() != JavaTokenType.RBRACKET) return false;
    iterator.retreat();
    if (iterator.getTokenType() != JavaTokenType.LBRACKET) return false;
    return true;
  }

  //need custom handler, since brace matcher cannot be used
  private static boolean handleJavaGT(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getTokenType() != JavaTokenType.GT) return false;
    while (!iterator.atEnd() && !BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.advance();
    }

    if (iterator.atEnd()) return false;
    if (BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.retreat();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance--;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance++;
      }
      else if (BraceMatchingUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.retreat();
    }

    if (balance == 0) {
      editor.getCaretModel().moveToOffset(offset + 1);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return true;
    }

    return false;
  }

  //need custom handler, since brace matcher cannot be used
  private static void handleAfterJavaLT(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    while (iterator.getStart() > 0 && !BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.retreat();
    }

    if (BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.advance();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance++;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance--;
      }
      else if (BraceMatchingUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.advance();
    }

    if (balance == 1) {
      editor.getDocument().insertString(offset, ">");
    }
  }

  private static boolean handleELOpeningBrace(final Editor editor, final PsiFile file, final Project project) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement elementAt = file.findElementAt(offset-1);

    // TODO: handle it with insertAfterLParen(...)
    if (!JspSpiUtil.isJavaContext(elementAt) &&
        ( elementAt.getText().equals("${") ||
          elementAt.getText().equals("#{")
        )
        ) {
      editor.getDocument().insertString(offset, "}");
      return true;
    }

    return false;
  }

  private static boolean handleELClosingBrace(final Editor editor, final PsiFile file, final Project project) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAt = file.findElementAt(offset);
    PsiElement parent = PsiTreeUtil.getParentOfType(elementAt,ELExpressionHolder.class);

    // TODO: handle it with insertAfterRParen(...)
    if (parent != null) {
      if (elementAt != null && elementAt.getText().equals("}")) {
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      }
    }

    return false;
  }

  private static void handleJspEqual(Project project, Editor editor) {
    final CharSequence chars = editor.getDocument().getCharsSequence();
    int current = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    JspFile file = PsiUtil.getJspFile(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
    PsiElement element = file.findElementAt(current);
    if (element == null) {
      element = file.findElementAt(editor.getDocument().getTextLength() - 1);
      if (element == null) return;
    }
    if (current >= 3 && chars.charAt(current-3) == '<' && chars.charAt(current-2)=='%')  {
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }

      int ptr = current;
      while(ptr < chars.length() && Character.isWhitespace(chars.charAt(ptr))) ++ptr;

      if (ptr + 1 >= chars.length() || (chars.charAt(ptr) != '%' || chars.charAt(ptr+1) != '>') ) {
        editor.getDocument().insertString(current,"%>");
      }
    }
  }

  private static boolean handleSemicolon(Editor editor, FileType fileType) {
    if (fileType != StdFileTypes.JAVA) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private static boolean handleXmlSlashInEmptyEnd(Project project, Editor editor){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    FileViewProvider provider = file.getViewProvider();
    PsiElement element = provider.findElementAt(offset, XMLLanguage.class);

    if (element instanceof XmlToken) {
      final IElementType tokenType = ((XmlToken)element).getTokenType();

      if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END &&
          offset == element.getTextOffset()
         ) {
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      } else if (tokenType == XmlTokenType.XML_TAG_END &&
                 offset == element.getTextOffset()
                ) {
        final ASTNode parentNode = element.getParent().getNode();
        final ASTNode child = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parentNode);

        if (child != null && offset + 1 == child.getTextRange().getStartOffset()) {
          editor.getDocument().replaceString(offset + 1, parentNode.getTextRange().getEndOffset(),"");
        }
      }
    }

    return false;
  }

  private static void handleXmlSlash(Project project, Editor editor){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    FileViewProvider provider = file.getViewProvider();
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = provider.findElementAt(offset - 1, XMLLanguage.class);
    if (element == null) return;
    if (!(element.getLanguage() instanceof XMLLanguage)) return;

    ASTNode prevLeaf = element.getNode();
    if (prevLeaf != null && !"/".equals(prevLeaf.getText())) return;
    while((prevLeaf = TreeUtil.prevLeaf(prevLeaf)) != null && prevLeaf.getElementType() == XmlTokenType.XML_WHITE_SPACE);
    if(prevLeaf instanceof OuterLanguageElement) {
      element = file.getDocument().findElementAt(offset - 1);
      prevLeaf = element.getNode();
      while((prevLeaf = TreeUtil.prevLeaf(prevLeaf)) != null && prevLeaf.getElementType() == XmlTokenType.XML_WHITE_SPACE);
    }
    if(prevLeaf == null) return;

    XmlTag tag = PsiTreeUtil.getParentOfType(prevLeaf.getPsi(), XmlTag.class);
    if(tag == null) { // prevLeaf maybe in one tree and element in another
      PsiElement element2 = provider.findElementAt(prevLeaf.getStartOffset(), XMLLanguage.class);
      tag = PsiTreeUtil.getParentOfType(element2, XmlTag.class);
      if (tag == null) return;
    }

    if (tag instanceof JspXmlTagBase || tag instanceof JspXmlRootTag) return;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return;
    if (PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class) != null) return;

    EditorModificationUtil.insertStringAtCaret(editor, ">");
  }

  private static boolean handleXmlGreater(Project project, Editor editor, FileType fileType){
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    FileViewProvider provider = file.getViewProvider();
    final int offset = editor.getCaretModel().getOffset();

    PsiElement element;

    if (offset < editor.getDocument().getTextLength()) {
      element = provider.findElementAt(offset, XMLLanguage.class);
      if (!(element instanceof PsiWhiteSpace)) {
        boolean nonAcceptableDelimiter = true;

        if (element instanceof XmlToken) {
          IElementType tokenType = ((XmlToken)element).getTokenType();

          if (tokenType == XmlTokenType.XML_START_TAG_START ||
              tokenType == XmlTokenType.XML_END_TAG_START
             ) {
            if (offset > 0) {
              PsiElement previousElement = provider.findElementAt(offset - 1, XMLLanguage.class);

              if (previousElement instanceof XmlToken) {
                tokenType = ((XmlToken)previousElement).getTokenType();
                element = previousElement;
                nonAcceptableDelimiter = false;
              }
            }
          }

          if (tokenType == XmlTokenType.XML_TAG_END ||
              tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END && element.getTextOffset() == offset - 1
             ) {
            editor.getCaretModel().moveToOffset(offset + 1);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            return true;
          }
        }
        if (nonAcceptableDelimiter) return false;
      } else {
        // check if right after empty end
        PsiElement previousElement = provider.findElementAt(offset - 1, XMLLanguage.class);
        if (previousElement instanceof XmlToken) {
          final IElementType tokenType = ((XmlToken)previousElement).getTokenType();

          if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
            return true;
          }
        }
      }

      PsiElement parent = element.getParent();
      if (parent instanceof XmlText) {
        final String text = parent.getText();
        // check /
        final int index = offset - parent.getTextOffset() - 1;

        if (index >= 0 && text.charAt(index)=='/') {
          return false; // already seen /
        }
        element = parent.getPrevSibling();
      } else if (parent instanceof XmlTag && !(element.getPrevSibling() instanceof XmlTag)) {
        element = parent;
      } else if (parent instanceof XmlAttributeValue) {
        element = parent;
      }
    }
    else {
      element = provider.findElementAt(editor.getDocument().getTextLength() - 1, XMLLanguage.class);
      if (element == null) return false;
      element = element.getParent();
    }

    if (element instanceof XmlAttributeValue) {
      element = element.getParent().getParent();
    }

    while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (element == null) return false;
    if (!(element instanceof XmlTag)) {
      if (element instanceof XmlTokenImpl &&
          element.getPrevSibling() !=null &&
          element.getPrevSibling().getText().equals("<")
         ) {
        // tag is started and there is another text in the end
        editor.getDocument().insertString(offset, "</" + element.getText() + ">");
      }
      return false;
    }

    XmlTag tag = (XmlTag)element;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return false;
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return false;
    if (tag instanceof JspXmlTagBase) return false;

    final String name = tag.getName();
    if (tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(name)) return false;
    if ("".equals(name)) return false;

    int tagOffset = tag.getTextRange().getStartOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(tagOffset);
    if (BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true,true)) return false;

    editor.getDocument().insertString(offset, "</" + name + ">");
    return false;
  }

  private static void handleAfterLParen(Editor editor, FileType fileType, char lparenChar){
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    boolean atEndOfDocument = offset == editor.getDocument().getTextLength();

    if (!atEndOfDocument) iterator.retreat();
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    IElementType braceTokenType = braceMatcher.getTokenType(lparenChar, iterator);
    if (iterator.atEnd() || iterator.getTokenType() != braceTokenType) return;

    if (!iterator.atEnd()) {
      iterator.advance();

      IElementType tokenType = !iterator.atEnd() ? iterator.getTokenType() : null;
      if (tokenType instanceof IJavaElementType) {
        if (!TokenTypeEx.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType)
            && tokenType != JavaTokenType.SEMICOLON
            && tokenType != JavaTokenType.COMMA
            && tokenType != JavaTokenType.RPARENTH
            && tokenType != JavaTokenType.RBRACKET
            && tokenType != JavaTokenType.RBRACE
        ) {
          return;
        }
      }

      iterator.retreat();
    }

    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, editor.getDocument().getCharsSequence(),fileType);
    if (lparenOffset < 0) lparenOffset = 0;

    iterator = ((EditorEx)editor).getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true);

    if (!matched){
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
      else {
        LOG.assertTrue(false);

        return;
      }
      editor.getDocument().insertString(offset, text);
    }
  }

  private static boolean handleRParen(Editor editor, FileType fileType, char rightParen, char leftParen){
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    if (iterator.atEnd() || braceMatcher.getTokenType(rightParen,iterator) != iterator.getTokenType()) {
      return false;
    }

    iterator.retreat();

    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceMatcher.getTokenType(leftParen, iterator),  editor.getDocument().getCharsSequence(),fileType);
    if (lparenthOffset < 0) return false;

    if (leftParen == '<' && !BraceMatchingUtil.isAfterClassLikeIdentifierOrDot(lparenthOffset, editor)) return false;

    iterator = ((EditorEx) editor).getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true);

    if (!matched) return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private boolean handleQuote(Editor editor, FileType fileType, char quote, DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return false;
    final QuoteHandler quoteHandler = quoteHandlers.get(fileType);
    if (quoteHandler == null) return false;

    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    CharSequence chars = editor.getDocument().getCharsSequence();
    int length = editor.getDocument().getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) return false;

    if (offset < length && chars.charAt(offset) == quote){
      if (isClosingQuote(editor, quoteHandler, offset)){
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      }
    }

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    if (!iterator.atEnd()){
      IElementType tokenType = iterator.getTokenType();
      if (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.JSP){
        if (tokenType instanceof IJavaElementType){
          if (!TokenTypeEx.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType)
              && tokenType != JavaTokenType.SEMICOLON
              && tokenType != JavaTokenType.COMMA
              && tokenType != JavaTokenType.RPARENTH
              && tokenType != JavaTokenType.RBRACKET
              && tokenType != JavaTokenType.RBRACE
              && tokenType != JavaTokenType.STRING_LITERAL
              && tokenType != JavaTokenType.CHARACTER_LITERAL
          ) {
            return false;
          }
        }
      }
    }

    myOriginalHandler.execute(editor, quote, dataContext);
    offset = editor.getCaretModel().getOffset();

    if (isOpeningQuote(editor, quoteHandler, offset - 1) &&
        hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      editor.getDocument().insertString(offset, String.valueOf(quote));
    }

    return true;
  }

  private static boolean isClosingQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isClosingQuote(iterator,offset);
  }

  private static boolean isOpeningQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private static boolean hasNonClosedLiterals(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private static boolean isTypingEscapeQuote(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = (offset - 1) - offset1;
    return (slashCount % 2) != 0 && isInsideLiteral(editor, quoteHandler, offset);
  }

  private static boolean isInsideLiteral(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset - 1);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isInsideLiteral(iterator);
  }

  private static void indentClosingBrace(final Project project, final Editor editor){
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.AUTOINDENT_CLOSING_BRACE) return;

    indentBrace(project, editor, '}', JavaTokenType.RBRACE);
  }

  private static void indentOpenedBrace(final Project project, final Editor editor){
    indentBrace(project, editor, '{', JavaTokenType.LBRACE);
  }

  private static void indentBrace(final Project project, final Editor editor, final char braceChar, final IElementType braceTokenType) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != braceChar) return;

    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      PsiDocumentManager.getInstance(project).commitDocument(document);

      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !file.isWritable()) return;
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == braceTokenType){
        final Runnable action = new Runnable() {
          public void run(){
            try{
              int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
              editor.getCaretModel().moveToOffset(newOffset + 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              editor.getSelectionModel().removeSelection();
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }
  }
}

