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

import com.intellij.formatting.*;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
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
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

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
          FormatterEx.getInstanceEx().format(
            model, mySettings,mySettings.getIndentOptions(fileToFormat.getFileType()), new FormatTextRanges(range, true)
          );

          wrapLongLinesIfNecessary(file, document, startOffset, endOffset);
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
    final Project project = file.getProject();
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
          for (FormatTextRanges.FormatTextRange range : ranges.getRanges()) {
            TextRange textRange = range.getTextRange();
            wrapLongLinesIfNecessary(file, document, textRange.getStartOffset(), textRange.getEndOffset());
          }
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

  /**
   * Inspects all lines of the given document and wraps all of them that exceed {@link CodeStyleSettings#RIGHT_MARGIN right margin}.
   * <p/>
   * I.e. the algorithm is to do the following for every line:
   * <p/>
   * <pre>
   * <ol>
   *   <li>
   *      Check if the line exceeds {@link CodeStyleSettings#RIGHT_MARGIN right margin}. Go to the next line in the case of
   *      negative answer;
   *   </li>
   *   <li>Determine line wrap position; </li>
   *   <li>
   *      Perform 'smart wrap', i.e. not only wrap the line but insert additional characters over than line feed if necessary.
   *      For example consider that we wrap a single-line comment - we need to insert comment symbols on a start of the wrapped
   *      part as well. Generally, we get the same behavior as during pressing 'Enter' at wrap position during editing document;
   *   </li>
   * </ol>
   </pre>
   *
   * @param file        file that holds parsed document tree
   * @param document    target document
   * @param startOffset start offset of the first line to check for wrapping (inclusive)
   * @param endOffset   end offset of the first line to check for wrapping (exclusive)
   */
  private void wrapLongLinesIfNecessary(@NotNull PsiFile file, @Nullable final Document document, final int startOffset,
                                        final int endOffset)
  {
    if (!mySettings.WRAP_LONG_LINES || file.getViewProvider().isLockedByPsiOperations()) {
      return;
    }

    Editor editor = PsiUtilBase.findEditor(file);
    EditorFactory editorFactory = null;
    if (editor == null) {
      if (document == null || !ApplicationManager.getApplication().isDispatchThread()) {
        return;
      }
      editorFactory = EditorFactory.getInstance();
      editor = editorFactory.createEditor(document);
    }
    try {
      final Editor editorToUse = editor;
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          doWrapLongLinesIfNecessary(editorToUse, editorToUse.getDocument(), startOffset, endOffset);
        }
      });
    }
    finally {
      if (editorFactory != null) {
        editorFactory.releaseEditor(editor);
      }
    }
  }

  private void doWrapLongLinesIfNecessary(@NotNull Editor editor, @NotNull Document document, int startOffset, int endOffset) {
    LineWrapPositionStrategy strategy = LanguageLineWrapPositionStrategy.INSTANCE.forEditor(editor);
    CharSequence text = document.getCharsSequence();
    int startLine = document.getLineNumber(startOffset);
    int endLine = document.getLineNumber(Math.min(document.getTextLength(), endOffset) - 1);
    int maxLine = Math.min(document.getLineCount(), endLine + 1);
    int tabSize = EditorUtil.getTabSize(editor);
    if (tabSize <= 0) {
      tabSize = 1;
    }
    int spaceSize = EditorUtil.getSpaceWidth(Font.PLAIN, editor);

    for (int line = startLine; line < maxLine; line++) {
      int startLineOffset = document.getLineStartOffset(line);
      int endLineOffset = document.getLineEndOffset(line);

      boolean hasTabs = false;
      boolean canOptimize = true;
      boolean hasNonSpaceSymbols = false;
      loop:
      for (int i = startLineOffset; i < Math.min(endLineOffset, endOffset); i++) {
        char c = text.charAt(i);
        switch (c) {
          case '\t': {
            hasTabs = true;
            if (hasNonSpaceSymbols) {
              canOptimize = false;
              break loop;
            }
          }
          case ' ': break;
          default: hasNonSpaceSymbols = true;
        }
      }

      int preferredWrapPosition = Integer.MAX_VALUE;
      if (!hasTabs) {
        if (Math.min(endLineOffset, endOffset) >= mySettings.RIGHT_MARGIN) {
          preferredWrapPosition = startLineOffset + mySettings.RIGHT_MARGIN - FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS;
        }
      }
      else if (canOptimize) {
        int width = 0;
        int symbolWidth;
        for (int i = startLineOffset; i < Math.min(endLineOffset, endOffset); i++) {
          char c = text.charAt(i);
          switch (c) {
            case '\t': symbolWidth = tabSize - (width % tabSize); break;
            default: symbolWidth = 1;
          }
          if (width + symbolWidth + FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS >= mySettings.RIGHT_MARGIN
              && (Math.min(endLineOffset, endOffset) - i) >= FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS)
          {
            preferredWrapPosition = i - 1;
            break;
          }
          width += symbolWidth;
        }
      }
      else {
        int width = 0;
        int x = 0;
        int newX;
        int symbolWidth;
        for (int i = startLineOffset; i < Math.min(endLineOffset, endOffset); i++) {
          char c = text.charAt(i);
          switch (c) {
            case '\t':
              newX = EditorUtil.nextTabStop(x, editor);
              int diffInPixels = newX - x;
              symbolWidth = diffInPixels / spaceSize;
              if (diffInPixels % spaceSize > 0) {
                symbolWidth++;
              }
              break;
            default: newX = x + EditorUtil.charWidth(c, Font.PLAIN, editor); symbolWidth = 1;
          }
          if (width + symbolWidth + FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS >= mySettings.RIGHT_MARGIN
              && (Math.min(endLineOffset, endOffset) - i) >= FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS)
          {
            preferredWrapPosition = i - 1;
            break;
          }
          x = newX;
          width += symbolWidth;
        }
      }
      if (preferredWrapPosition >= endLineOffset) {
        continue;
      }
      if (preferredWrapPosition >= endOffset) {
        return;
      }

      // We know that current line exceeds right margin if control flow reaches this place, so, wrap it.
      int wrapOffset = strategy.calculateWrapPosition(
        text, Math.max(startLineOffset, startOffset), Math.min(endLineOffset, endOffset), preferredWrapPosition, false
      );
      editor.getCaretModel().moveToOffset(wrapOffset);
      DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());

      SelectionModel selectionModel = editor.getSelectionModel();
      boolean restoreSelection;
      int startSelectionOffset = 0;
      int endSelectionOffset = 0;
      if (restoreSelection = selectionModel.hasSelection()) {
        startSelectionOffset = selectionModel.getSelectionStart();
        endSelectionOffset = selectionModel.getSelectionEnd();
        selectionModel.removeSelection();
      }
      int textLengthBeforeWrap = document.getTextLength();
      EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, dataContext);
      if (restoreSelection) {
        int symbolsDiff = document.getTextLength() - textLengthBeforeWrap;
        int newSelectionStart = startSelectionOffset;
        int newSelectionEnd = endSelectionOffset;
        if (startSelectionOffset >= wrapOffset) {
          newSelectionStart += symbolsDiff;
        }
        if (endSelectionOffset >= wrapOffset) {
          newSelectionEnd += symbolsDiff;
        }
        selectionModel.setSelection(newSelectionStart, newSelectionEnd);
      }


      // There is a possible case that particular line is so long, that its part that exceeds right margin and is wrapped
      // still exceeds right margin. Hence, we recursively call 'wrap long lines' sub-routine in order to handle that.

      doWrapLongLinesIfNecessary(editor, document, document.getLineStartOffset(line + 1), endOffset);
      return;
    }
  }
}

