package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;

public class CodeFormatterFacade {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade");

  private CodeStyleSettings mySettings;
  private Helper myHelper;

  public CodeFormatterFacade(CodeStyleSettings settings, Helper helper) {
    mySettings = settings;
    myHelper = helper;
  }

  public ASTNode process(ASTNode element, int parent_indent) {
    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile();
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder != null) {
      TextRange range = element.getTextRange();
      return processRange(element, range.getStartOffset(), range.getEndOffset());
    }

    return element;
  }

  public ASTNode processRange(final ASTNode element, final int startOffset, final int endOffset) {
    final FileType fileType = myHelper.getFileType();

    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile();
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    final Document document = file.getViewProvider().getDocument();
    final RangeMarker rangeMarker = document != null && endOffset < document.getTextLength()? document.createRangeMarker(startOffset, endOffset):null;

    if (builder != null) {
      TextRange range = preprocess(element, startOffset, endOffset);
      //final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
      final PsiFile containingFile = psiElement.getContainingFile();
      final FormattingModel model = builder.createModel(psiElement, mySettings);
      if (containingFile.getTextLength() > 0) {
        try {
          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileType), range, true);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      /*
       */
      if (!psiElement.isValid()) {
        if (rangeMarker != null) {
          final PsiElement at = file.findElementAt(rangeMarker.getStartOffset());
          final PsiElement result = PsiTreeUtil.getParentOfType(at, psiElement.getClass(), false);
          assert result != null;
          return result.getNode();
        } else {
          assert false;
        }
      }

//      return SourceTreeToPsiMap.psiElementToTree(pointer.getElement());

    }

    return element;
  }

  public void processText(final PsiFile file, final int startOffset, final int endOffset) {
    processText(file, startOffset, endOffset, true);
  }

  private void processText(final PsiFile file, final int startOffset, final int endOffset, boolean headWhitespace) {
    final FileType fileType = myHelper.getFileType();

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);

    if (builder != null) {
      if (file.getTextLength() > 0) {
        try {
          TextRange range = preprocess(file.getNode(), startOffset, endOffset);
          final PostprocessReformattingAspect component = file.getProject().getComponent(PostprocessReformattingAspect.class);
          component.doPostponedFormatting(file.getViewProvider());
          Block rootBlock= builder.createModel(file, mySettings).getRootBlock();
          Project project = file.getProject();
          final FormattingModel model = new DocumentBasedFormattingModel(rootBlock,
                                                                         PsiDocumentManager.getInstance(project).getDocument(file),
                                                                         project, mySettings, fileType, file);

          //printToConsole(rootBlock, model);

          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileType), range, headWhitespace);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void printToConsole(final Block rootBlock, final FormattingModel model) {
    try {
      String tree = JDOMUtil.writeElement(new FormatInfoPrinter(rootBlock, model.getDocumentModel()).blocksAsTree(), "\n");
      System.out.println("---TREE---");
      System.out.println(tree);
      System.out.println("---/TREE---");
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void processTextWithoutHeadWhitespace(final PsiFile file, final int startOffset, final int endOffset) {
    final FileType fileType = myHelper.getFileType();

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);

    if (builder != null) {
      if (file.getTextLength() > 0) {
        try {
          TextRange range = preprocess(file.getNode(), startOffset, endOffset);
          FormattingModel originalModel = builder.createModel(file, mySettings);
          Project project = file.getProject();
          final FormattingModel model = new DocumentBasedFormattingModel(originalModel.getRootBlock(),
            PsiDocumentManager.getInstance(project).getDocument(file),
            project, mySettings, fileType, file);

          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileType), range, false);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private static TextRange preprocess(final ASTNode node, final int startOffset, final int endOffset) {
    TextRange result = new TextRange(startOffset, endOffset);
    for(PreFormatProcessor processor: Extensions.getExtensions(PreFormatProcessor.EP_NAME)) {
      result = processor.process(node, result);
  }
    return result;
  }
}

