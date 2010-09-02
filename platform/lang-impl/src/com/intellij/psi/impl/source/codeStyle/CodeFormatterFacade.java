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
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class CodeFormatterFacade {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade");

  private final CodeStyleSettings mySettings;

  public CodeFormatterFacade(CodeStyleSettings settings) {
    mySettings = settings;
  }

  public ASTNode processElement(ASTNode element) {
    TextRange range = element.getTextRange();
    return processRange(element, range.getStartOffset(), range.getEndOffset());
  }

  public ASTNode processRange(final ASTNode element, final int startOffset, final int endOffset) {
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    final PsiFile file = psiElement.getContainingFile();
    final Document document = file.getViewProvider().getDocument();
    final RangeMarker rangeMarker = document != null && endOffset < document.getTextLength()? document.createRangeMarker(startOffset, endOffset):null;

    PsiElement elementToFormat = document instanceof DocumentWindow ? InjectedLanguageUtil.getTopLevelFile(file) : psiElement;
    final PsiFile fileToFormat = elementToFormat.getContainingFile();

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(fileToFormat);
    if (builder != null) {
      TextRange range = preprocess(element, startOffset, endOffset);
      if (document instanceof DocumentWindow) {
        DocumentWindow documentWindow = (DocumentWindow)document;
        range = documentWindow.injectedToHost(range);
      }

      //final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
      final FormattingModel model = builder.createModel(elementToFormat, mySettings);
      if (file.getTextLength() > 0) {
        try {
          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileToFormat.getFileType()), new FormatTextRanges(range, true));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

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

  public void processText(PsiFile file, final FormatTextRanges ranges, boolean doPostponedFormatting) {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document instanceof DocumentWindow) {
      file = InjectedLanguageUtil.getTopLevelFile(file);
      final DocumentWindow documentWindow = (DocumentWindow)document;
      for (FormatTextRanges.FormatTextRange range : ranges.getRanges()) {
        range.setTextRange(documentWindow.injectedToHost(range.getTextRange()));
      }
      document = documentWindow.getDelegate();
    }


    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);

    if (builder != null) {
      if (file.getTextLength() > 0) {
        try {
          ranges.preprocess(file.getNode());
          if (doPostponedFormatting) {
            RangeMarker[] markers = new RangeMarker[ranges.getRanges().size()];
            int i = 0;
            for (FormatTextRanges.FormatTextRange range : ranges.getRanges()) {
              TextRange textRange = range.getTextRange();
              int start = textRange.getStartOffset();
              int end = textRange.getEndOffset();
              if (start >= 0 && end > start && end <= document.getTextLength()) {
                markers[i] = document.createRangeMarker(textRange);
                markers[i].setGreedyToLeft(true);
                markers[i].setGreedyToRight(true);
                i++;
              }
            }
            final PostprocessReformattingAspect component = file.getProject().getComponent(PostprocessReformattingAspect.class);
            component.doPostponedFormatting(file.getViewProvider());
            i = 0;
            for (FormatTextRanges.FormatTextRange range : ranges.getRanges()) {
              if (markers[i] != null) {
                range.setTextRange(new TextRange(markers[i].getStartOffset(), markers[i].getEndOffset()));
              }
              i++;
            }
          }
          final FormattingModel originalModel = builder.createModel(file, mySettings);
          final FormattingModel model = new DocumentBasedFormattingModel(originalModel.getRootBlock(),
                                                                         document,
                                                                         project, mySettings, file.getFileType(), file);

          FormatterEx.getInstanceEx().format(model, mySettings, mySettings.getIndentOptions(file.getFileType()), ranges);
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

