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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleManagerImpl extends CodeStyleManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl");
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
    for (ASTNode child = file.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      transformAllChildren(child);
    }
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
    Helper helper = HelperFactory.createHelper(file.getFileType(), myProject);
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
      if (helper.getIndent(element, true) == 0) {
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

  @Nullable
  public static PsiElement insertNewLineIndentMarker(@NotNull PsiFile file, int offset) throws IncorrectOperationException {
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
    parent.addChild(marker, space1.getTreeNext());
    return SourceTreeToPsiMap.treeElementToPsi(marker);
  }

  public Indent getIndent(String text, FileType fileType) {
    int indent = HelperFactory.createHelper(fileType, myProject).getIndent(text, true);
    int indenLevel = indent / Helper.INDENT_FACTOR;
    int spaceCount = indent - indenLevel * Helper.INDENT_FACTOR;
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
    return HelperFactory.createHelper(fileType, myProject).fillIndent(indentLevel * Helper.INDENT_FACTOR + spaceCount);
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
}
