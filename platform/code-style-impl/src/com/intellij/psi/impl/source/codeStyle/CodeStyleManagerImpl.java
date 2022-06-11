// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.formatting.*;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorFacade;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.lineIndent.FormatterBasedIndentAdjuster;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.MathUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CodeStyleManagerImpl extends CodeStyleManager implements FormattingModeAwareIndentAdjuster {
  private static final Logger LOG = Logger.getInstance(CodeStyleManagerImpl.class);

  private final ThreadLocal<FormattingMode> myCurrentFormattingMode = ThreadLocal.withInitial(() -> FormattingMode.REFORMAT);

  private final Project myProject;

  public CodeStyleManagerImpl(Project project) {
    myProject = project;
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

    final PsiFile file = element.getContainingFile();

    if (file == null)
      return element;

    return FormattingServiceUtil.formatElement(element, canChangeWhiteSpacesOnly);
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
  public void reformatText(@NotNull PsiFile file, @NotNull Collection<? extends TextRange> ranges) throws IncorrectOperationException {
    reformatText(file, ranges, null);
  }

  @Override
  public void reformatTextWithContext(@NotNull PsiFile file,
                                      @NotNull ChangedRangesInfo info) throws IncorrectOperationException
  {
    ensureDocumentCommitted(file);
    FormatTextRanges formatRanges = new FormatTextRanges(info, ChangedRangesUtil.processChangedRanges(file, info));
    formatRanges.setExtendToContext(true);
    reformatText(file, formatRanges, null);
  }



  public void reformatText(@NotNull PsiFile file, @NotNull Collection<? extends TextRange> ranges, @Nullable Editor editor) throws IncorrectOperationException {
    FormatTextRanges formatRanges = new FormatTextRanges();
    ranges.forEach((range) -> formatRanges.add(range, true));
    reformatText(file, formatRanges, editor);
  }

  private void reformatText(@NotNull PsiFile file,
                            @NotNull FormatTextRanges ranges,
                            @Nullable Editor editor) throws IncorrectOperationException
  {
    if (ranges.isEmpty()) {
      return;
    }
    ensureDocumentCommitted(file);

    CheckUtil.checkWritable(file);
    if (!SourceTreeToPsiMap.hasTreeElement(file)) {
      return;
    }

    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(file);
    transformAllChildren(treeElement);

    LOG.assertTrue(file.isValid(), "File name: " + file.getName() + " , class: " + file.getClass().getSimpleName());

    if (editor == null) {
      editor = PsiEditorUtil.findEditor(file);
    }

    CaretPositionKeeper caretKeeper = null;
    if (editor != null) {
      caretKeeper = new CaretPositionKeeper(editor, getSettings(file), file.getLanguage());
    }

    if (FormatterUtil.isFormatterCalledExplicitly()) {
      removeEndingWhiteSpaceFromEachRange(file, ranges);
    }

    FormattingServiceUtil.formatRanges(file, ranges, false);

    if (caretKeeper != null) {
      caretKeeper.restoreCaretPosition();
    }
  }

  private void ensureDocumentCommitted(@NotNull PsiFile file) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
    if (document != null) {
      documentManager.commitDocument(document);
    }
  }

  private static void removeEndingWhiteSpaceFromEachRange(@NotNull PsiFile file, @NotNull FormatTextRanges ranges) {
    for (FormatTextRange formatRange : ranges.getRanges()) {
      TextRange range = formatRange.getTextRange();

      final int rangeStart = range.getStartOffset();
      final int rangeEnd = range.getEndOffset();

      PsiElement lastElementInRange = CoreCodeStyleUtil.findElementInTreeWithFormatterEnabled(file, rangeEnd);
      if (lastElementInRange instanceof PsiWhiteSpace && rangeStart < lastElementInRange.getTextRange().getStartOffset()) {
        PsiElement prev = lastElementInRange.getPrevSibling();
        if (prev != null) {
          int newEnd = prev.getTextRange().getEndOffset();
          formatRange.setTextRange(new TextRange(rangeStart, newEnd));
        }
      }
    }
  }

  private static PsiElement reformatRangeImpl(final @NotNull PsiElement element,
                                              final int startOffset,
                                              final int endOffset,
                                              boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException {
    LOG.assertTrue(element.isValid());
    CheckUtil.checkWritable(element);
    if( !SourceTreeToPsiMap.hasTreeElement( element ) )
    {
      return element;
    }

    return FormattingServiceUtil.formatElement(element, TextRange.create(startOffset, endOffset), canChangeWhiteSpacesOnly);
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
    assert containingFile != null;

    TextRange textRange = addedElement.getTextRange();
    final Document document = fileViewProvider.getDocument();
    if (document instanceof DocumentWindow) {
      containingFile = InjectedLanguageManager.getInstance(containingFile.getProject()).getTopLevelFile(containingFile);
      textRange = ((DocumentWindow)document).injectedToHost(textRange);
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(containingFile);
    if (builder != null) {
      final FormattingModel model = CoreFormatterUtil.buildModel(builder, containingFile, getSettings(containingFile), FormattingMode.REFORMAT);
      FormatterEx.getInstanceEx().formatAroundRange(model, getSettings(containingFile), containingFile, textRange);
    }

    adjustLineIndent(containingFile, textRange);
  }

  @Override
  public int adjustLineIndent(@NotNull final PsiFile file, final int offset) throws IncorrectOperationException {
    return PostprocessReformattingAspect.getInstance(file.getProject()).disablePostprocessFormattingInside(
      () -> doAdjustLineIndentByOffset(file, offset, FormattingMode.ADJUST_INDENT));
  }

  @Override
  public int adjustLineIndent(@NotNull final Document document, final int offset, FormattingMode mode) {
    return PostprocessReformattingAspect.getInstance(getProject()).disablePostprocessFormattingInside(() -> {
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      documentManager.commitDocument(document);

      PsiFile file = documentManager.getPsiFile(document);
      if (file == null) return offset;

      return doAdjustLineIndentByOffset(file, offset, mode);
    });
  }

  @Override
  public int adjustLineIndent(@NotNull Document document, int offset) {
    return adjustLineIndent(document, offset, FormattingMode.ADJUST_INDENT);
  }

  private int doAdjustLineIndentByOffset(@NotNull PsiFile file, int offset, FormattingMode mode) {
    final Integer result = new CodeStyleManagerRunnable<Integer>(this, mode) {
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
    new CodeStyleManagerRunnable<>(this, FormattingMode.ADJUST_INDENT) {
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
    return getLineIndent(file, offset, FormattingMode.ADJUST_INDENT);
  }

  @Override
  @Nullable
  public String getLineIndent(@NotNull PsiFile file, int offset, FormattingMode mode) {
    return new CodeStyleManagerRunnable<String>(this, mode) {
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
  public List<String> getLineIndents(@NotNull PsiFile file) {
    return new CodeStyleManagerRunnable<List<String>>(this, FormattingMode.ADJUST_INDENT) {
      @Override
      protected boolean useDocumentBaseFormattingModel() {
        return false;
      }

      @Override
      protected List<String> doPerform(int offset, TextRange range) {
        return FormatterEx.getInstanceEx().getLineIndents(myModel, mySettings, myIndentOptions);
      }
    }.perform(file, 0, null, null);
  }

  @Override
  @Nullable
  public String getLineIndent(@NotNull Document document, int offset) {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null) return "";

    return getLineIndent(file, offset);
  }

  @Override
  @Deprecated
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
    ASTNode element = SourceTreeToPsiMap.psiElementToTree(CoreCodeStyleUtil.findElementInTreeWithFormatterEnabled(file, end));
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
    if (getSettings(file).getCommonSettings(file.getLanguage()).KEEP_FIRST_COLUMN_COMMENT && isCommentToken(element)) {
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

  @Override
  public Indent getIndent(String text, FileType fileType) {
    int indent = IndentHelperImpl.getIndent(CodeStyle.getSettings(myProject).getIndentOptions(fileType), text, true);
    int indentLevel = indent / IndentHelperImpl.INDENT_FACTOR;
    int spaceCount = indent - indentLevel * IndentHelperImpl.INDENT_FACTOR;
    return new IndentImpl(CodeStyle.getSettings(myProject), indentLevel, spaceCount, fileType);
  }

  @Override
  public String fillIndent(Indent indent, FileType fileType) {
    IndentImpl indent1 = (IndentImpl)indent;
    int indentLevel = indent1.getIndentLevel();
    int spaceCount = indent1.getSpaceCount();
    final CodeStyleSettings settings = CodeStyle.getSettings(myProject);
    if (indentLevel < 0) {
      spaceCount += indentLevel * settings.getIndentSize(fileType);
      indentLevel = 0;
      if (spaceCount < 0) {
        spaceCount = 0;
      }
    }
    else {
      if (spaceCount < 0) {
        int v = (-spaceCount + settings.getIndentSize(fileType) - 1) / settings.getIndentSize(fileType);
        indentLevel -= v;
        spaceCount += v * settings.getIndentSize(fileType);
        if (indentLevel < 0) {
          indentLevel = 0;
        }
      }
    }
    return IndentHelperImpl.fillIndent(myProject, fileType, indentLevel * IndentHelperImpl.INDENT_FACTOR + spaceCount);
  }

  @Override
  public Indent zeroIndent() {
    return new IndentImpl(CodeStyle.getSettings(myProject), 0, 0, null);
  }

  @NotNull
  private static CodeStyleSettings getSettings(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file);
  }

  @Override
  public boolean isSequentialProcessingAllowed() {
    return CoreCodeStyleUtil.isSequentialProcessingAllowed();
  }

  /**
   * @deprecated Use {@link CoreCodeStyleUtil#setSequentialProcessingAllowed(boolean)}
   */
  @Deprecated(forRemoval = true)
  public static void setSequentialProcessingAllowed(boolean allowed) {
    CoreCodeStyleUtil.setSequentialProcessingAllowed(allowed);
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
    final PostprocessReformattingAspect component = PostprocessReformattingAspect.getInstance(getProject());
    return component.disablePostprocessFormattingInside(r);
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
    boolean myBlankLineIndentPreserved;

    CaretPositionKeeper(@NotNull Editor editor, @NotNull CodeStyleSettings settings, @NotNull Language language) {
      myEditor = editor;
      myCaretModel = editor.getCaretModel();
      myDocument = editor.getDocument();
      myBlankLineIndentPreserved = isBlankLineIndentPreserved(settings, language);

      int caretOffset = getCaretOffset();
      int lineStartOffset = getLineStartOffsetByTotalOffset(caretOffset);
      int lineEndOffset = getLineEndOffsetByTotalOffset(caretOffset);
      boolean shouldFixCaretPosition = CharArrayUtil.isEmptyOrSpaces(myDocument.getCharsSequence(), lineStartOffset, lineEndOffset);

      if (shouldFixCaretPosition) {
        initRestoreInfo(caretOffset);
      }
    }

    private static boolean isBlankLineIndentPreserved(@NotNull CodeStyleSettings settings, @NotNull Language language) {
      CommonCodeStyleSettings langSettings = settings.getCommonSettings(language);
      CommonCodeStyleSettings.IndentOptions indentOptions = langSettings.getIndentOptions();
      return indentOptions != null && indentOptions.KEEP_INDENTS_ON_EMPTY_LINES;
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
      Project project = myEditor.getProject();
      if (project == null || PsiDocumentManager.getInstance(project).isDocumentBlockedByPsi(myDocument)) {
        return;
      }
      insertWhiteSpaceIndentIfNeeded(newCaretLineStartOffset);
    }

    private void restoreVisualPosition() {
      if (myVisualColumnToRestore < 0) {
        EditorFacade.getInstance().runWithAnimationDisabled(myEditor, () -> myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE));
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
      int upperBound = Math.max(myDocument.getTextLength() - 1, 0);
      caretOffset = MathUtil.clamp(caretOffset, 0, upperBound);
      return caretOffset;
    }

    private boolean lineContainsWhiteSpaceSymbolsOnly(int lineNumber) {
      int startOffset = myDocument.getLineStartOffset(lineNumber);
      int endOffset = myDocument.getLineEndOffset(lineNumber);
      return CharArrayUtil.isEmptyOrSpaces(myDocument.getCharsSequence(), startOffset, endOffset);
    }

    private int getCurrentCaretLine() {
      return myDocument.getLineNumber(myCaretModel.getOffset());
    }
  }

  @Override
  public FormattingMode getCurrentFormattingMode() {
    return myCurrentFormattingMode.get();
  }

  void setCurrentFormattingMode(@NotNull FormattingMode mode) {
    myCurrentFormattingMode.set(mode);
  }

  @Override
  public int getSpacing(@NotNull PsiFile file, int offset) {
    FormattingModel model = createFormattingModel(file, offset);
    return model == null ? -1 : FormatterEx.getInstance().getSpacingForBlockAtOffset(model, offset);
  }

  @Override
  public int getMinLineFeeds(@NotNull PsiFile file, int offset) {
    FormattingModel model = createFormattingModel(file, offset);
    return model == null ? -1 : FormatterEx.getInstance().getMinLineFeedsBeforeBlockAtOffset(model, offset);
  }

  @Nullable
  private static FormattingModel createFormattingModel(@NotNull PsiFile file, int offset) {
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder == null) return null;
    CodeStyleSettings settings = CodeStyle.getSettings(file);
    return builder.createModel(FormattingContext.create(file, TextRange.create(offset, offset), settings, FormattingMode.REFORMAT));
  }

  @Override
  public void runWithDocCommentFormattingDisabled(@NotNull PsiFile file, @NotNull Runnable runnable) {
    DocCommentSettings docSettings = getDocCommentSettings(file);
    boolean currDocFormattingEnabled = docSettings.isDocFormattingEnabled();
    docSettings.setDocFormattingEnabled(false);
    try {
      runnable.run();
    }
    finally {
      docSettings.setDocFormattingEnabled(currDocFormattingEnabled);
    }
  }

  @Override
  @NotNull
  public DocCommentSettings getDocCommentSettings(@NotNull PsiFile file) {
    Language language = file.getLanguage();
    LanguageCodeStyleProvider settingsProvider = LanguageCodeStyleProvider.forLanguage(language);
    if (settingsProvider != null) {
      return settingsProvider.getDocCommentSettings(CodeStyle.getSettings(file));
    }
    return DocCommentSettings.DEFAULTS;
  }

  @Override
  public void scheduleIndentAdjustment(@NotNull Document document, int offset) {
    FormatterBasedIndentAdjuster.scheduleIndentAdjustment(myProject, document, offset);
  }

  @Override
  public void scheduleReformatWhenSettingsComputed(@NotNull PsiFile file) {
    if (ModelBranch.getPsiBranch(file) != null) {
      commitAndFormat(file);
      return;
    }

    final Runnable commandRunnable = () -> WriteCommandAction.runWriteCommandAction(
      myProject, CodeStyleBundle.message("command.name.reformat"), null, () -> commitAndFormat(file), file);

    CodeStyleCachingService.getInstance(myProject).scheduleWhenSettingsComputed(
      file,
      () -> {
        //noinspection SSBasedInspection
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          commandRunnable.run();
        }
        else {
          ApplicationManager.getApplication().invokeLater(commandRunnable);
        }
      }
    );
  }

  private void commitAndFormat(@NotNull PsiFile file) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
    if (document != null) {
      documentManager.commitDocument(document);
    }
    PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(() -> reformat(ensureValid(file)));
  }

  @NotNull
  private PsiFile ensureValid(@NotNull PsiFile file) {
    if (!file.isValid()) {
      return PsiUtilCore.getPsiFile(myProject, file.getViewProvider().getVirtualFile());
    }
    return file;
  }
}
