/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.FormatTextRanges;
import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class CodeStyleManagerImpl extends CodeStyleManager {
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl");
  private static final ThreadLocal<ProcessingUnderProgressInfo> SEQUENTIAL_PROCESSING_ALLOWED
    = new ThreadLocal<ProcessingUnderProgressInfo>()
  {
    @Override
    protected ProcessingUnderProgressInfo initialValue() {
      return new ProcessingUnderProgressInfo();
    }
  };
  
  private final Project myProject;
  @NonNls private static final String DUMMY_IDENTIFIER = "xxx";

  public CodeStyleManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public PsiElement reformat(@NotNull PsiElement element) throws IncorrectOperationException {
    return reformat(element, false);
  }

  @NotNull
  public PsiElement reformat(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if( !SourceTreeToPsiMap.hasTreeElement( element ) )
    {
      return element;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(element);
    final PsiElement formatted = SourceTreeToPsiMap.treeElementToPsi(new CodeFormatterFacade(getSettings()).processElement(treeElement));
    if (!canChangeWhiteSpacesOnly) {
      return postProcessElement(formatted);
    } else {
      return formatted;
    }

  }

  private PsiElement postProcessElement(final PsiElement formatted) {
    PsiElement result = formatted;
    for (PostFormatProcessor postFormatProcessor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
      result = postFormatProcessor.processElement(result, getSettings());
    }
    return result;
  }

  private void postProcessText(final PsiFile file, final TextRange textRange) {
    TextRange currentRange = textRange;
    for (final PostFormatProcessor myPostFormatProcessor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
      currentRange = myPostFormatProcessor.processText(file, currentRange, getSettings());
    }
  }

  public PsiElement reformatRange(@NotNull PsiElement element,
                                  int startOffset,
                                  int endOffset,
                                  boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    return reformatRangeImpl(element, startOffset, endOffset, canChangeWhiteSpacesOnly);
  }

  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset)
    throws IncorrectOperationException {
    return reformatRangeImpl(element, startOffset, endOffset, false);

  }

  private static void transformAllChildren(final ASTNode file) {
    ((TreeElement)file).acceptTree(new RecursiveTreeElementWalkingVisitor() {
    });
  }


  public void reformatText(@NotNull PsiFile file, int startOffset, int endOffset) throws IncorrectOperationException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    CheckUtil.checkWritable(file);
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(file);
    transformAllChildren(treeElement);

    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings());
    LOG.assertTrue(file.isValid());
    final PsiElement start = findElementInTreeWithFormatterEnabled(file, startOffset);
    final PsiElement end = findElementInTreeWithFormatterEnabled(file, endOffset);
    if (start != null && !start.isValid()) {
      LOG.error("start=" + start + "; file=" + file);
    }
    if (end != null && !end.isValid()) {
      LOG.error("end=" + start + "; end=" + file);
    }

    Editor editor = PsiUtilBase.findEditor(file);
    
    // There is a possible case that cursor is located at the end of the line that contains only white spaces. For example:
    //     public void foo() {
    //         <caret>
    //     }
    // Formatter removes such white spaces, i.e. keeps only line feed symbol. But we want to preserve caret position then.
    // So, we check if it should be preserved and restore it after formatting if necessary
    int visualColumnToRestore = -1;
    
    if (editor != null) {
      Document document = editor.getDocument();
      int caretOffset = editor.getCaretModel().getOffset();
      caretOffset = Math.max(Math.min(caretOffset, document.getTextLength() - 1), 0);
      CharSequence text = document.getCharsSequence();
      int caretLine = document.getLineNumber(caretOffset);
      int lineStartOffset = document.getLineStartOffset(caretLine);
      int lineEndOffset = document.getLineEndOffset(caretLine);
      boolean fixCaretPosition = true;
      for (int i = lineStartOffset; i < lineEndOffset; i++) {
        char c = text.charAt(i);
        if (c != ' ' && c != '\t' && c != '\n') {
          fixCaretPosition = false;
          break;
        }
      }
      if (fixCaretPosition) {
        visualColumnToRestore = editor.getCaretModel().getVisualPosition().column;
      }
    }
    

    boolean formatFromStart = startOffset == 0;
    boolean formatToEnd = endOffset == file.getTextLength();

    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(getProject());
    final SmartPsiElementPointer startPointer = start == null ? null : smartPointerManager.createSmartPsiElementPointer(start);

    final SmartPsiElementPointer endPointer = end == null ? null : smartPointerManager.createSmartPsiElementPointer(end);

    codeFormatter.processText(file, new FormatTextRanges(new TextRange(startOffset, endOffset), true), true);
    final PsiElement startElement = startPointer == null ? null : startPointer.getElement();
    final PsiElement endElement = endPointer == null ? null : endPointer.getElement();

    if ((startElement != null || formatFromStart) && (endElement != null || formatToEnd)) {
      postProcessText(file, new TextRange(formatFromStart ? 0 : startElement.getTextRange().getStartOffset(),
                                          formatToEnd ? file.getTextLength() : endElement.getTextRange().getEndOffset()));
    }

    if (visualColumnToRestore < 0) {
      return;
    }
    CaretModel caretModel = editor.getCaretModel();
    VisualPosition position = caretModel.getVisualPosition();
    if (visualColumnToRestore != position.column) {
      caretModel.moveToVisualPosition(new VisualPosition(position.line, visualColumnToRestore));
    }
  }

  private PsiElement reformatRangeImpl(final PsiElement element,
                                       final int startOffset,
                                       final int endOffset,
                                       boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    LOG.assertTrue(element.isValid());
    CheckUtil.checkWritable(element);
    if( !SourceTreeToPsiMap.hasTreeElement( element ) )
    {
      return element;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(element);
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings());
    final PsiElement formatted = SourceTreeToPsiMap.treeElementToPsi(codeFormatter.processRange(treeElement, startOffset, endOffset));

    return canChangeWhiteSpacesOnly ? formatted : postProcessElement(formatted);
  }


  public void reformatNewlyAddedElement(@NotNull final ASTNode parent, @NotNull final ASTNode addedElement) throws IncorrectOperationException {

    LOG.assertTrue(addedElement.getTreeParent() == parent, "addedElement must be added to parent");

    final PsiElement psiElement = parent.getPsi();

    PsiFile containingFile = psiElement.getContainingFile();
    final FileViewProvider fileViewProvider = containingFile.getViewProvider();
    if (fileViewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
      containingFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
    }

    TextRange textRange = addedElement.getTextRange();
    final Document document = fileViewProvider.getDocument();
    if (document instanceof DocumentWindow) {
      containingFile = InjectedLanguageUtil.getTopLevelFile(containingFile);
      textRange = ((DocumentWindow)document).injectedToHost(textRange);
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(containingFile);
    if (builder != null) {
      final FormattingModel model = builder.createModel(containingFile, getSettings());
      FormatterEx.getInstanceEx().formatAroundRange(model, getSettings(), textRange, containingFile.getFileType());
    }

    adjustLineIndent(containingFile, textRange);
  }

  public int adjustLineIndent(@NotNull final PsiFile file, final int offset) throws IncorrectOperationException {
    return PostprocessReformattingAspect.getInstance(file.getProject()).disablePostprocessFormattingInside(new Computable<Integer>() {
      public Integer compute() {
        return doAdjustLineIndentByOffset(file, offset);
      }
    });
  }

  @Nullable
  static PsiElement findElementInTreeWithFormatterEnabled(final PsiFile file, final int offset) {
    final PsiElement bottomost = file.findElementAt(offset);
    if (bottomost != null && LanguageFormatting.INSTANCE.forContext(bottomost) != null){
      return bottomost;
    }

    final Language fileLang = file.getLanguage();
    if (fileLang instanceof CompositeLanguage) {
      return file.getViewProvider().findElementAt(offset, fileLang);
    }

    return bottomost;
  }

  public int adjustLineIndent(@NotNull final Document document, final int offset) {
    return PostprocessReformattingAspect.getInstance(getProject()).disablePostprocessFormattingInside(new Computable<Integer>() {
      public Integer compute() {
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        documentManager.commitDocument(document);

        PsiFile file = documentManager.getPsiFile(document);
        if (file == null) return offset;

        return doAdjustLineIndentByOffset(file, offset);
      }
    });
  }

  private int doAdjustLineIndentByOffset(@NotNull PsiFile file, int offset) {
    return new CodeStyleManagerRunnable<Integer>(this) {
      @Override
      protected Integer doPerform(int offset, TextRange range) {
        return FormatterEx.getInstanceEx().adjustLineIndent(myModel, mySettings, myIndentOptions, offset, mySignificantRange);
      }

      @Override
      protected Integer computeValueInsidePlainComment(PsiFile file, int offset, Integer defaultValue) {
        return CharArrayUtil.shiftForward(file.getViewProvider().getContents(), offset, " \t");
      }

      @Override
      protected Integer adjustResultForInjected(Integer result, DocumentWindow documentWindow) {
        return documentWindow.hostToInjected(result);
      }
    }.perform(file, offset, null, offset);
  }

  public void adjustLineIndent(@NotNull PsiFile file, TextRange rangeToAdjust) throws IncorrectOperationException {
    new CodeStyleManagerRunnable<Object>(this) {
      @Override
      protected Object doPerform(int offset, TextRange range) {
        FormatterEx.getInstanceEx().adjustLineIndentsForRange(myModel, mySettings, myIndentOptions, range);
        return null;
      }
    }.perform(file, -1, rangeToAdjust, null);
  }

  @Nullable
  public String getLineIndent(@NotNull PsiFile file, int offset) {
    return new CodeStyleManagerRunnable<String>(this) {
      @Override
      protected boolean useDocumentBaseFormattingModel() {
        return false;
      }

      @Override
      protected String doPerform(int offset, TextRange range) {
        return FormatterEx.getInstanceEx().getLineIndent(myModel, mySettings, myIndentOptions, offset, mySignificantRange);
      }
    }.perform(file, offset, null, null);
  }

  @Nullable
  public String getLineIndent(@NotNull Editor editor) {
    Document doc = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    if (offset >= doc.getTextLength()) {
      return "";
    }

    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(doc);
    if (file == null) return "";

    return getLineIndent(file, offset);
  }

  public boolean isLineToBeIndented(@NotNull PsiFile file, int offset) {
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return false;
    }
    IndentHelper indentHelper = HelperFactory.createHelper(file.getFileType(), myProject);
    CharSequence chars = file.getViewProvider().getContents();
    int start = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (start > 0 && chars.charAt(start) != '\n' && chars.charAt(start) != '\r') {
      return false;
    }
    int end = CharArrayUtil.shiftForward(chars, offset, " \t");
    if (end >= chars.length()) {
      return false;
    }
    ASTNode element = SourceTreeToPsiMap.psiElementToTree(findElementInTreeWithFormatterEnabled(file, end));
    if (element == null) {
      return false;
    }
    if (element.getElementType() == TokenType.WHITE_SPACE) {
      return false;
    }
    if (element.getElementType() == PlainTextTokenTypes.PLAIN_TEXT) {
      return false;
    }
    /*
    if( element.getElementType() instanceof IJspElementType )
    {
      return false;
    }
    */
    if (getSettings().KEEP_FIRST_COLUMN_COMMENT && isCommentToken(element)) {
      if (indentHelper.getIndent(element, true) == 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCommentToken(final ASTNode element) {
    final Language language = element.getElementType().getLanguage();
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    if (commenter instanceof CodeDocumentationAwareCommenter) {
      final CodeDocumentationAwareCommenter documentationAwareCommenter = (CodeDocumentationAwareCommenter)commenter;
      return element.getElementType() == documentationAwareCommenter.getBlockCommentTokenType() ||
             element.getElementType() == documentationAwareCommenter.getLineCommentTokenType();
    }
    return false;
  }
  
  private static boolean isWhiteSpaceSymbol(char c) {
    return c == ' ' || c == '\t' || c == '\n';
  }

  /**
   * Formatter trims line that contains white spaces symbols only, however, there is a possible case that we want
   * to preserve them for particular line (e.g. for live template that defines blank line that contains $END$ marker).
   * <p/>
   * Current approach is to do the following:
   * <pre>
   * <ol>
   *   <li>Insert dummy text at the end of the blank line which white space symbols should be preserved;</li>
   *   <li>Perform formatting;</li>
   *   <li>Remove dummy text;</li>
   * </ol>
   * </pre>
   * <p/>
   * This method inserts that dummy text if necessary (if target line contains white space symbols only).
   * <p/>
   * Please note that it tries to do that via PSI at first (checks if given offset points to
   * {@link TokenType#WHITE_SPACE white space element} and inserts dummy text as dedicated element if necessary) and,
   * in case of the negative answer, tries to perform the examination considering document just as a sequence of characters
   * and assuming that white space symbols are white spaces, tabulations and line feeds. The rationale for such an approach is:
   * <pre>
   * <ul>
   *   <li>
   *      there is a possible case that target language considers symbols over than white spaces, tabulations and line feeds
   *      to be white spaces and the answer lays at PSI structure of the file;
   *   </li>
   *   <li>
   *      dummy text inserted during PSI-based processing has {@link TokenType#NEW_LINE_INDENT special type} that may be treated
   *      specifically during formatting;
   *   </li>
   * </ul>
   * </pre>
   * <p/>
   * <b>Note:</b> it's expected that the whole white space region that contains given offset is processed in a way that all
   * {@link RangeMarker range markers} registered for the given offset are expanded to the whole white space region.
   * E.g. there is a possible case that particular range marker serves for defining formatting range, hence, its start/end offsets
   * are updated correspondingly after current method call and whole white space region is reformatted.
   *
   * @param document    target document
   * @param offset      offset that defines end boundary of the target line text fragment (start boundary is the first line's symbol)
   * @return            text range that points to the newly inserted dummy text if any; <code>null</code> otherwise
   */
  @Nullable
  public static TextRange insertNewLineIndentMarker(@NotNull PsiFile file, @NotNull Document document, int offset) 
    throws IncorrectOperationException 
  {
    TextRange result = insertNewLineIndentMarker(file, offset);
    if (result == null) {
      result = insertNewLineIndentMarker(document, offset);
    }
    return result;
  }
  
  
  @Nullable
  private static TextRange insertNewLineIndentMarker(@NotNull Document document, final int offset) {
    CharSequence text = document.getCharsSequence();
    if (offset < 0 || offset >= text.length() || !isWhiteSpaceSymbol(text.charAt(offset))) {
      return null;
    }
    
    int start = offset;
    for (int i = offset - 1; i >= 0; i--) {
      if (!isWhiteSpaceSymbol(text.charAt(i))) {
        break;
      }
      start = i;
    }
    
    int end = offset;
    for (; end < text.length(); end++) {
      if (!isWhiteSpaceSymbol(text.charAt(end))) {
        break;
      }
    }
    
    StringBuilder buffer = new StringBuilder();
    buffer.append(text.subSequence(start, end));
    
    // Modify the document in order to expand range markers pointing to the given offset to the whole white space range.
    document.deleteString(start, end);
    document.insertString(start, buffer);
    
    setSequentialProcessingAllowed(false);
    document.insertString(offset, DUMMY_IDENTIFIER);
    return new TextRange(offset, offset + DUMMY_IDENTIFIER.length());
  }

  @Nullable
  private static TextRange insertNewLineIndentMarker(@NotNull PsiFile file, int offset) throws IncorrectOperationException {
    CheckUtil.checkWritable(file);
    final CharTable charTable = ((FileElement)SourceTreeToPsiMap.psiElementToTree(file)).getCharTable();
    PsiElement elementAt = findElementInTreeWithFormatterEnabled(file, offset);
    if( elementAt == null )
    {
      return null;
    }
    ASTNode element = SourceTreeToPsiMap.psiElementToTree(elementAt);
    ASTNode parent = element.getTreeParent();
    int elementStart = element.getTextRange().getStartOffset();
    if (element.getElementType() != TokenType.WHITE_SPACE) {
      /*
      if (elementStart < offset) return null;
      Element marker = Factory.createLeafElement(ElementType.NEW_LINE_INDENT, "###".toCharArray(), 0, "###".length());
      ChangeUtil.addChild(parent, marker, element);
      return marker;
      */
      return null;
    }

    ASTNode space1 = splitSpaceElement((TreeElement)element, offset - elementStart, charTable);
    ASTNode marker = Factory.createSingleLeafElement(TokenType.NEW_LINE_INDENT, DUMMY_IDENTIFIER, charTable, file.getManager());
    setSequentialProcessingAllowed(false);
    parent.addChild(marker, space1.getTreeNext());
    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(marker);
    return psiElement == null ? null : psiElement.getTextRange();
  }

  public Indent getIndent(String text, FileType fileType) {
    int indent = HelperFactory.createHelper(fileType, myProject).getIndent(text, true);
    int indenLevel = indent / IndentHelper.INDENT_FACTOR;
    int spaceCount = indent - indenLevel * IndentHelper.INDENT_FACTOR;
    return new IndentImpl(getSettings(), indenLevel, spaceCount, fileType);
  }

  public String fillIndent(Indent indent, FileType fileType) {
    IndentImpl indent1 = (IndentImpl)indent;
    int indentLevel = indent1.getIndentLevel();
    int spaceCount = indent1.getSpaceCount();
    if (indentLevel < 0) {
      spaceCount += indentLevel * getSettings().getIndentSize(fileType);
      indentLevel = 0;
      if (spaceCount < 0) {
        spaceCount = 0;
      }
    }
    else {
      if (spaceCount < 0) {
        int v = (-spaceCount + getSettings().getIndentSize(fileType) - 1) / getSettings().getIndentSize(fileType);
        indentLevel -= v;
        spaceCount += v * getSettings().getIndentSize(fileType);
        if (indentLevel < 0) {
          indentLevel = 0;
        }
      }
    }
    return HelperFactory.createHelper(fileType, myProject).fillIndent(indentLevel * IndentHelper.INDENT_FACTOR + spaceCount);
  }

  public Indent zeroIndent() {
    return new IndentImpl(getSettings(), 0, 0, null);
  }


  private static ASTNode splitSpaceElement(TreeElement space, int offset, CharTable charTable) {
    LOG.assertTrue(space.getElementType() == TokenType.WHITE_SPACE);
    CharSequence chars = space.getChars();
    LeafElement space1 = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, chars, 0, offset, charTable, SharedImplUtil.getManagerByTree(space));
    LeafElement space2 = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, chars, offset, chars.length(), charTable, SharedImplUtil.getManagerByTree(space));
    ASTNode parent = space.getTreeParent();
    parent.replaceChild(space, space1);
    parent.addChild(space2, space1.getTreeNext());
    return space1;
  }

  private CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(myProject);
  }

  @Override
  public boolean isSequentialProcessingAllowed() {
    return SEQUENTIAL_PROCESSING_ALLOWED.get().isAllowed();
  }

  /**
   * Allows to define if {@link #isSequentialProcessingAllowed() sequential processing} should be allowed.
   * <p/>
   * Current approach is not allow to stop sequential processing for more than predefine amount of time (couple of seconds).
   * That means that call to this method with <code>'true'</code> argument is not mandatory for successful processing even
   * if this method is called with <code>'false'</code> argument before.
   * 
   * @param allowed     flag that defines if {@link #isSequentialProcessingAllowed() sequential processing} should be allowed
   */
  public static void setSequentialProcessingAllowed(boolean allowed) {
    ProcessingUnderProgressInfo info = SEQUENTIAL_PROCESSING_ALLOWED.get();
    if (allowed) {
      info.decrement();
    }
    else {
      info.increment();
    }
  }
  
  private static class ProcessingUnderProgressInfo {
    
    private static final long DURATION_TIME = TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS);
    
    private int  myCount;
    private long myEndTime;
    
    public void increment() {
      if (myCount > 0 && System.currentTimeMillis() > myEndTime) {
        myCount = 0;
      }
      myCount++;
      myEndTime = System.currentTimeMillis() + DURATION_TIME;
    }
    
    public void decrement() {
      if (myCount <= 0) {
        return;
      }
      myCount--;
    }
    
    public boolean isAllowed() {
      return myCount <= 0 || System.currentTimeMillis() >= myEndTime;
    }
  }
}
