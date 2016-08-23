/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.formatting.*;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.codeStyle.autodetect.DetectedIndentOptionsNotificationProvider;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CodeStyleManagerImpl extends CodeStyleManager {
  private static final Logger LOG = Logger.getInstance(CodeStyleManagerImpl.class);
  private static final ThreadLocal<ProcessingUnderProgressInfo> SEQUENTIAL_PROCESSING_ALLOWED
    = new ThreadLocal<ProcessingUnderProgressInfo>()
  {
    @Override
    protected ProcessingUnderProgressInfo initialValue() {
      return new ProcessingUnderProgressInfo();
    }
  };

  private final FormatterTagHandler myTagHandler;

  private final Project myProject;
  @NonNls private static final String DUMMY_IDENTIFIER = "xxx";

  public CodeStyleManagerImpl(Project project) {
    myProject = project;
    myTagHandler = new FormatterTagHandler(getSettings());
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public PsiElement reformat(@NotNull PsiElement element) throws IncorrectOperationException {
    return reformat(element, false);
  }

  @Override
  @NotNull
  public PsiElement reformat(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if( !SourceTreeToPsiMap.hasTreeElement( element ) )
    {
      return element;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(element);
    final PsiElement formatted = SourceTreeToPsiMap.treeElementToPsi(new CodeFormatterFacade(getSettings(), element.getLanguage()).processElement(treeElement));
    if (!canChangeWhiteSpacesOnly) {
      return postProcessElement(formatted);
    }
    return formatted;
  }

  private PsiElement postProcessElement(@NotNull final PsiElement formatted) {
    PsiElement result = formatted;
    if (getSettings().FORMATTER_TAGS_ENABLED && formatted instanceof PsiFile) {
      postProcessEnabledRanges((PsiFile) formatted, formatted.getTextRange(), getSettings());
    }
    else {
      for (PostFormatProcessor postFormatProcessor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
        result = postFormatProcessor.processElement(result, getSettings());
      }
    }
    return result;
  }

  private void postProcessText(@NotNull final PsiFile file, @NotNull final TextRange textRange) {
    if (!getSettings().FORMATTER_TAGS_ENABLED) {
      TextRange currentRange = textRange;
      for (final PostFormatProcessor myPostFormatProcessor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
        currentRange = myPostFormatProcessor.processText(file, currentRange, getSettings());
      }
    }
    else {
      postProcessEnabledRanges(file, textRange, getSettings());
    }
  }

  @Override
  public PsiElement reformatRange(@NotNull PsiElement element,
                                  int startOffset,
                                  int endOffset,
                                  boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    return reformatRangeImpl(element, startOffset, endOffset, canChangeWhiteSpacesOnly);
  }

  @Override
  public PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset)
    throws IncorrectOperationException {
    return reformatRangeImpl(element, startOffset, endOffset, false);

  }

  private static void transformAllChildren(final ASTNode file) {
    ((TreeElement)file).acceptTree(new RecursiveTreeElementWalkingVisitor() {
    });
  }


  @Override
  public void reformatText(@NotNull PsiFile file, int startOffset, int endOffset) throws IncorrectOperationException {
    reformatText(file, Collections.singleton(new TextRange(startOffset, endOffset)));
  }

  @Override
  public void reformatText(@NotNull PsiFile file, @NotNull Collection<TextRange> ranges) throws IncorrectOperationException {
    reformatText(file, ranges, null);
  }

  @Override
  public void reformatTextWithContext(@NotNull PsiFile file, 
                                      @NotNull ChangedRangesInfo info) throws IncorrectOperationException 
  {
    FormatTextRanges formatRanges = new FormatTextRanges(info);
    reformatText(file, formatRanges, null, true);
  }

  public void reformatText(@NotNull PsiFile file, @NotNull Collection<TextRange> ranges, @Nullable Editor editor) throws IncorrectOperationException {
    FormatTextRanges formatRanges = new FormatTextRanges();
    ranges.forEach((range) -> formatRanges.add(range, true));
    reformatText(file, formatRanges, editor, false);
  }
  
  private void reformatText(@NotNull PsiFile file, 
                            @NotNull FormatTextRanges ranges,
                            @Nullable Editor editor, 
                            boolean reformatContext) throws IncorrectOperationException 
  {
    if (ranges.isEmpty()) {
      return;
    }
    boolean isFullReformat = ranges.isFullReformat(file);
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    CheckUtil.checkWritable(file);
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(file);
    transformAllChildren(treeElement);

    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(), file.getLanguage());
    codeFormatter.setReformatContext(reformatContext);

    LOG.assertTrue(file.isValid(), "File name: " + file.getName() + " , class: " + file.getClass().getSimpleName());

    if (editor == null) {
      editor = PsiUtilBase.findEditor(file);
    }

    CaretPositionKeeper caretKeeper = null;
    if (editor != null) {
      caretKeeper = new CaretPositionKeeper(editor, getSettings(), file.getLanguage());
    }

    if (FormatterUtil.isFormatterCalledExplicitly()) {
      removeEndingWhiteSpaceFromEachRange(file, ranges);
    }

    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(getProject());
    List<RangeFormatInfo> infos = new ArrayList<>();
    for (TextRange range : ranges.getTextRanges()) {
      final PsiElement start = findElementInTreeWithFormatterEnabled(file, range.getStartOffset());
      final PsiElement end = findElementInTreeWithFormatterEnabled(file, range.getEndOffset());
      if (start != null && !start.isValid()) {
        LOG.error("start=" + start + "; file=" + file);
      }
      if (end != null && !end.isValid()) {
        LOG.error("end=" + start + "; end=" + file);
      }
      boolean formatFromStart = range.getStartOffset() == 0;
      boolean formatToEnd = range.getEndOffset() == file.getTextLength();
      infos.add(new RangeFormatInfo(
        start == null ? null : smartPointerManager.createSmartPsiElementPointer(start),
        end == null ? null : smartPointerManager.createSmartPsiElementPointer(end),
        formatFromStart,
        formatToEnd
      ));
    }

    codeFormatter.processText(file, ranges, true);
    
    for (RangeFormatInfo info : infos) {
      final PsiElement startElement = info.startPointer == null ? null : info.startPointer.getElement();
      final PsiElement endElement = info.endPointer == null ? null : info.endPointer.getElement();
      if ((startElement != null || info.fromStart) && (endElement != null || info.toEnd)) {
        postProcessText(file, new TextRange(info.fromStart ? 0 : startElement.getTextRange().getStartOffset(),
                                            info.toEnd ? file.getTextLength() : endElement.getTextRange().getEndOffset()));
      }
      if (info.startPointer != null) smartPointerManager.removePointer(info.startPointer);
      if (info.endPointer != null) smartPointerManager.removePointer(info.endPointer);
    }

    if (caretKeeper != null) {
      caretKeeper.restoreCaretPosition();
    }
    if (editor instanceof EditorEx && isFullReformat) {
      ((EditorEx)editor).reinitSettings();
      DetectedIndentOptionsNotificationProvider.updateIndentNotification(file, true);
    }
  }

  private static void removeEndingWhiteSpaceFromEachRange(@NotNull PsiFile file, @NotNull FormatTextRanges ranges) {
    for (FormatTextRange formatRange : ranges.getRanges()) {
      TextRange range = formatRange.getTextRange();
      
      final int rangeStart = range.getStartOffset();
      final int rangeEnd = range.getEndOffset();

      PsiElement lastElementInRange = findElementInTreeWithFormatterEnabled(file, rangeEnd);
      if (lastElementInRange instanceof PsiWhiteSpace && rangeStart < lastElementInRange.getTextRange().getStartOffset()) {
        PsiElement prev = lastElementInRange.getPrevSibling();
        if (prev != null) {
          int newEnd = prev.getTextRange().getEndOffset();
          formatRange.setTextRange(new TextRange(rangeStart, newEnd));
        }
      }
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
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(), element.getLanguage());
    final PsiElement formatted = SourceTreeToPsiMap.treeElementToPsi(codeFormatter.processRange(treeElement, startOffset, endOffset));

    return canChangeWhiteSpacesOnly ? formatted : postProcessElement(formatted);
  }


  @Override
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
      containingFile = InjectedLanguageManager.getInstance(containingFile.getProject()).getTopLevelFile(containingFile);
      textRange = ((DocumentWindow)document).injectedToHost(textRange);
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(containingFile);
    if (builder != null) {
      final FormattingModel model = CoreFormatterUtil.buildModel(builder, containingFile, getSettings(), FormattingMode.REFORMAT);
      FormatterEx.getInstanceEx().formatAroundRange(model, getSettings(), containingFile, textRange);
    }

    adjustLineIndent(containingFile, textRange);
  }

  @Override
  public int adjustLineIndent(@NotNull final PsiFile file, final int offset) throws IncorrectOperationException {
    DetectedIndentOptionsNotificationProvider.updateIndentNotification(file, false);
    return PostprocessReformattingAspect.getInstance(file.getProject()).disablePostprocessFormattingInside(
      () -> doAdjustLineIndentByOffset(file, offset));
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

  @Override
  public int adjustLineIndent(@NotNull final Document document, final int offset) {
    return PostprocessReformattingAspect.getInstance(getProject()).disablePostprocessFormattingInside(() -> {
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      documentManager.commitDocument(document);

      PsiFile file = documentManager.getPsiFile(document);
      if (file == null) return offset;

      return doAdjustLineIndentByOffset(file, offset);
    });
  }

  private int doAdjustLineIndentByOffset(@NotNull PsiFile file, int offset) {
    final Integer result = new CodeStyleManagerRunnable<Integer>(this, FormattingMode.ADJUST_INDENT) {
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
        return result != null ? documentWindow.hostToInjected(result)
                              : null;
      }
    }.perform(file, offset, null, null);
    
    return result != null ? result : offset;
  }

  @Override
  public void adjustLineIndent(@NotNull PsiFile file, TextRange rangeToAdjust) throws IncorrectOperationException {
    new CodeStyleManagerRunnable<Object>(this, FormattingMode.ADJUST_INDENT) {
      @Override
      protected Object doPerform(int offset, TextRange range) {
        FormatterEx.getInstanceEx().adjustLineIndentsForRange(myModel, mySettings, myIndentOptions, range);
        return null;
      }
    }.perform(file, -1, rangeToAdjust, null);
  }

  @Override
  @Nullable
  public String getLineIndent(@NotNull PsiFile file, int offset) {
    return new CodeStyleManagerRunnable<String>(this, FormattingMode.ADJUST_INDENT) {
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

  @Override
  @Nullable
  public String getLineIndent(@NotNull Document document, int offset) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null) return "";

    return getLineIndent(file, offset);
  }

  @Override
  public boolean isLineToBeIndented(@NotNull PsiFile file, int offset) {
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return false;
    }
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
      if (IndentHelper.getInstance().getIndent(myProject, file.getFileType(), element, true) == 0) {
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
   * to preserve them for particular line 
   * (e.g. for live template that defines line with whitespaces that contains $END$ marker: templateText   $END$).
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
   * This method inserts that dummy comment (fallback to identifier <code>xxx</code>, see {@link CodeStyleManagerImpl#createDummy(PsiFile)})
   * if necessary.
   * <p/>

   * <b>Note:</b> it's expected that the whole white space region that contains given offset is processed in a way that all
   * {@link RangeMarker range markers} registered for the given offset are expanded to the whole white space region.
   * E.g. there is a possible case that particular range marker serves for defining formatting range, hence, its start/end offsets
   * are updated correspondingly after current method call and whole white space region is reformatted.
   *
   * @param file        target PSI file
   * @param document    target document
   * @param offset      offset that defines end boundary of the target line text fragment (start boundary is the first line's symbol)
   * @return            text range that points to the newly inserted dummy text if any; <code>null</code> otherwise
   * @throws IncorrectOperationException  if given file is read-only
   */
  @Nullable
  public static TextRange insertNewLineIndentMarker(@NotNull PsiFile file, @NotNull Document document, int offset) {
    CharSequence text = document.getImmutableCharSequence();
    if (offset <= 0 || offset >= text.length() || !isWhiteSpaceSymbol(text.charAt(offset))) {
      return null;
    }
    
    if (!isWhiteSpaceSymbol(text.charAt(offset - 1))) {
      return null; // no whitespaces before offset
    }

    int end = offset;
    for (; end < text.length(); end++) {
      if (text.charAt(end) == '\n') {
        break; // line is empty till the end
      }
      if (!isWhiteSpaceSymbol(text.charAt(end))) {
        return null;
      }
    }

    setSequentialProcessingAllowed(false);
    String dummy = createDummy(file);
    document.insertString(offset, dummy);
    return new TextRange(offset, offset + dummy.length());
  }

  @NotNull
  private static String createDummy(@NotNull PsiFile file) {
    Language language = file.getLanguage();
    PsiComment comment = null;
    try {
      comment = PsiParserFacade.SERVICE.getInstance(file.getProject()).createLineOrBlockCommentFromText(language, "");
    }
    catch (Throwable ignored) {
    }
    String text = comment != null ? comment.getText() : null;
    return text != null ? text : DUMMY_IDENTIFIER;
  }

  /**
   * Allows to check if given offset points to white space element within the given PSI file and return that white space
   * element in the case of positive answer.
   *
   * @param file    target file
   * @param offset  offset that might point to white space element within the given PSI file
   * @return        target white space element for the given offset within the given file (if any); <code>null</code> otherwise
   */
  @Nullable
  public static PsiElement findWhiteSpaceNode(@NotNull PsiFile file, int offset) {
    return doFindWhiteSpaceNode(file, offset).first;
  }

  @NotNull
  private static Pair<PsiElement, CharTable> doFindWhiteSpaceNode(@NotNull PsiFile file, int offset) {
    ASTNode astNode = SourceTreeToPsiMap.psiElementToTree(file);
    if (!(astNode instanceof FileElement)) {
      return new Pair<>(null, null);
    }
    PsiElement elementAt = InjectedLanguageUtil.findInjectedElementNoCommit(file, offset);
    final CharTable charTable = ((FileElement)astNode).getCharTable();
    if (elementAt == null) {
      elementAt = findElementInTreeWithFormatterEnabled(file, offset);
    }

    if( elementAt == null) {
      return new Pair<>(null, charTable);
    }
    ASTNode node = elementAt.getNode();
    if (node == null || node.getElementType() != TokenType.WHITE_SPACE) {
      return new Pair<>(null, charTable);
    }
    return Pair.create(elementAt, charTable);
  }

  @Override
  public Indent getIndent(String text, FileType fileType) {
    int indent = IndentHelperImpl.getIndent(myProject, fileType, text, true);
    int indenLevel = indent / IndentHelperImpl.INDENT_FACTOR;
    int spaceCount = indent - indenLevel * IndentHelperImpl.INDENT_FACTOR;
    return new IndentImpl(getSettings(), indenLevel, spaceCount, fileType);
  }

  @Override
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
    return IndentHelperImpl.fillIndent(myProject, fileType, indentLevel * IndentHelperImpl.INDENT_FACTOR + spaceCount);
  }

  @Override
  public Indent zeroIndent() {
    return new IndentImpl(getSettings(), 0, 0, null);
  }


  @NotNull
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

  @Override
  public void performActionWithFormatterDisabled(final Runnable r) {
    performActionWithFormatterDisabled(() -> {
      r.run();
      return null;
    });
  }

  @Override
  public <T extends Throwable> void performActionWithFormatterDisabled(final ThrowableRunnable<T> r) throws T {
    final Throwable[] throwable = new Throwable[1];

    performActionWithFormatterDisabled(() -> {
      try {
        r.run();
      }
      catch (Throwable t) {
        throwable[0] = t;
      }
      return null;
    });

    if (throwable[0] != null) {
      //noinspection unchecked
      throw (T)throwable[0];
    }
  }

  @Override
  public <T> T performActionWithFormatterDisabled(final Computable<T> r) {
    return ((FormatterImpl)FormatterEx.getInstance()).runWithFormattingDisabled(() -> {
      final PostprocessReformattingAspect component = PostprocessReformattingAspect.getInstance(getProject());
      return component.disablePostprocessFormattingInside(r);
    });
  }

  private static class RangeFormatInfo{
    private final SmartPsiElementPointer startPointer;
    private final SmartPsiElementPointer endPointer;
    private final boolean                fromStart;
    private final boolean                toEnd;

    RangeFormatInfo(@Nullable SmartPsiElementPointer startPointer,
                    @Nullable SmartPsiElementPointer endPointer,
                    boolean fromStart,
                    boolean toEnd)
    {
      this.startPointer = startPointer;
      this.endPointer = endPointer;
      this.fromStart = fromStart;
      this.toEnd = toEnd;
    }
  }

  // There is a possible case that cursor is located at the end of the line that contains only white spaces. For example:
  //     public void foo() {
  //         <caret>
  //     }
  // Formatter removes such white spaces, i.e. keeps only line feed symbol. But we want to preserve caret position then.
  // So, if 'virtual space in editor' is enabled, we save target visual column. Caret indent is ensured otherwise
  private static class CaretPositionKeeper {
    Editor myEditor;
    Document myDocument;
    CaretModel myCaretModel;
    RangeMarker myBeforeCaretRangeMarker;
    String myCaretIndentToRestore;
    int myVisualColumnToRestore = -1;
    boolean myBlankLineIndentPreserved = true;

    CaretPositionKeeper(@NotNull Editor editor, @NotNull CodeStyleSettings settings, @NotNull Language language) {
      myEditor = editor;
      myCaretModel = editor.getCaretModel();
      myDocument = editor.getDocument();
      myBlankLineIndentPreserved = isBlankLineIndentPreserved(settings, language);

      int caretOffset = getCaretOffset();
      int lineStartOffset = getLineStartOffsetByTotalOffset(caretOffset);
      int lineEndOffset = getLineEndOffsetByTotalOffset(caretOffset);
      boolean shouldFixCaretPosition = rangeHasWhiteSpaceSymbolsOnly(myDocument.getCharsSequence(), lineStartOffset, lineEndOffset);

      if (shouldFixCaretPosition) {
        initRestoreInfo(caretOffset);
      }
    }

    private static boolean isBlankLineIndentPreserved(@NotNull CodeStyleSettings settings, @NotNull Language language) {
      CommonCodeStyleSettings langSettings = settings.getCommonSettings(language);
      if (langSettings != null) {
        CommonCodeStyleSettings.IndentOptions indentOptions = langSettings.getIndentOptions();
        return indentOptions != null && indentOptions.KEEP_INDENTS_ON_EMPTY_LINES;
      }
      return false;
    }

    private void initRestoreInfo(int caretOffset) {
      int lineStartOffset = getLineStartOffsetByTotalOffset(caretOffset);

      myVisualColumnToRestore = myCaretModel.getVisualPosition().column;
      myCaretIndentToRestore = myDocument.getText(TextRange.create(lineStartOffset, caretOffset));
      myBeforeCaretRangeMarker = myDocument.createRangeMarker(0, lineStartOffset);
    }

    public void restoreCaretPosition() {
      if (isVirtualSpaceEnabled()) {
        restoreVisualPosition();
      }
      else {
        restorePositionByIndentInsertion();
      }
    }

    private void restorePositionByIndentInsertion() {
      if (myBeforeCaretRangeMarker == null ||
          !myBeforeCaretRangeMarker.isValid() ||
          myCaretIndentToRestore == null ||
          myBlankLineIndentPreserved) {
        return;
      }
      int newCaretLineStartOffset = myBeforeCaretRangeMarker.getEndOffset();
      myBeforeCaretRangeMarker.dispose();
      if (myCaretModel.getVisualPosition().column == myVisualColumnToRestore) {
        return;
      }
      insertWhiteSpaceIndentIfNeeded(newCaretLineStartOffset);
    }

    private void restoreVisualPosition() {
      if (myVisualColumnToRestore < 0) {
        myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return;
      }
      VisualPosition position = myCaretModel.getVisualPosition();
      if (myVisualColumnToRestore != position.column) {
        myCaretModel.moveToVisualPosition(new VisualPosition(position.line, myVisualColumnToRestore));
      }
    }

    private void insertWhiteSpaceIndentIfNeeded(int caretLineOffset) {
      int lineToInsertIndent = myDocument.getLineNumber(caretLineOffset);
      if (!lineContainsWhiteSpaceSymbolsOnly(lineToInsertIndent))
        return;

      int lineToInsertStartOffset = myDocument.getLineStartOffset(lineToInsertIndent);

      if (lineToInsertIndent != getCurrentCaretLine()) {
        myCaretModel.moveToOffset(lineToInsertStartOffset);
      }
      myDocument.replaceString(lineToInsertStartOffset, caretLineOffset, myCaretIndentToRestore);
    }

    private boolean rangeHasWhiteSpaceSymbolsOnly(CharSequence text, int lineStartOffset, int lineEndOffset) {
      for (int i = lineStartOffset; i < lineEndOffset; i++) {
        char c = text.charAt(i);
        if (c != ' ' && c != '\t' && c != '\n') {
          return false;
        }
      }
      return true;
    }

    private boolean isVirtualSpaceEnabled() {
      return myEditor.getSettings().isVirtualSpace();
    }

    private int getLineStartOffsetByTotalOffset(int offset) {
      int line = myDocument.getLineNumber(offset);
      return myDocument.getLineStartOffset(line);
    }

    private int getLineEndOffsetByTotalOffset(int offset) {
      int line = myDocument.getLineNumber(offset);
      return myDocument.getLineEndOffset(line);
    }

    private int getCaretOffset() {
      int caretOffset = myCaretModel.getOffset();
      caretOffset = Math.max(Math.min(caretOffset, myDocument.getTextLength() - 1), 0);
      return caretOffset;
    }

    private boolean lineContainsWhiteSpaceSymbolsOnly(int lineNumber) {
      int startOffset = myDocument.getLineStartOffset(lineNumber);
      int endOffset = myDocument.getLineEndOffset(lineNumber);
      return rangeHasWhiteSpaceSymbolsOnly(myDocument.getCharsSequence(), startOffset, endOffset);
    }

    private int getCurrentCaretLine() {
      return myDocument.getLineNumber(myCaretModel.getOffset());
    }
  }

  private TextRange postProcessEnabledRanges(@NotNull final PsiFile file, @NotNull TextRange range, CodeStyleSettings settings) {
    TextRange result = TextRange.create(range.getStartOffset(), range.getEndOffset());
    List<TextRange> enabledRanges = myTagHandler.getEnabledRanges(file.getNode(), result);
    int delta = 0;
    for (TextRange enabledRange : enabledRanges) {
      enabledRange = enabledRange.shiftRight(delta);
      for (PostFormatProcessor processor : Extensions.getExtensions(PostFormatProcessor.EP_NAME)) {
        TextRange processedRange = processor.processText(file, enabledRange, settings);
        delta += processedRange.getLength() - enabledRange.getLength();
      }
    }
    result = result.grown(delta);
    return result;
  }
}
