/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class CodeFormatterFacade {
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade");
  
  private static final String WRAP_LINE_COMMAND_NAME = "AutoWrapLongLine";

  /**
   * This key is used as a flag that indicates if <code>'wrap long line during formatting'</code> activity is performed now.
   *
   * @see CodeStyleSettings#WRAP_LONG_LINES
   */
  public static final Key<Boolean> WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY 
    = new Key<Boolean>("WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY");

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
    assert psiElement != null;
    final PsiFile file = psiElement.getContainingFile();
    final Document document = file.getViewProvider().getDocument();
    final RangeMarker rangeMarker = document != null && endOffset < document.getTextLength()? document.createRangeMarker(startOffset, endOffset):null;

    PsiElement elementToFormat = document instanceof DocumentWindow ? InjectedLanguageUtil.getTopLevelFile(file) : psiElement;
    final PsiFile fileToFormat = elementToFormat.getContainingFile();

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(fileToFormat);
    if (builder != null) {
      TextRange range = preprocess(element, TextRange.create(startOffset, endOffset));
      if (document instanceof DocumentWindow) {
        DocumentWindow documentWindow = (DocumentWindow)document;
        range = documentWindow.injectedToHost(range);
      }

      //final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
      final FormattingModel model = CoreFormatterUtil.buildModel(builder, elementToFormat, mySettings, FormattingMode.REFORMAT);
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
    final List<FormatTextRanges.FormatTextRange> textRanges = ranges.getRanges();
    if (document instanceof DocumentWindow) {
      file = InjectedLanguageUtil.getTopLevelFile(file);
      final DocumentWindow documentWindow = (DocumentWindow)document;
      for (FormatTextRanges.FormatTextRange range : textRanges) {
        range.setTextRange(documentWindow.injectedToHost(range.getTextRange()));
      }
      document = documentWindow.getDelegate();
    }


    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);

    if (builder != null) {
      if (file.getTextLength() > 0) {
        try {
          final PsiElement startElement = file.findElementAt(textRanges.get(0).getTextRange().getStartOffset());
          final PsiElement endElement = file.findElementAt(textRanges.get(textRanges.size() - 1).getTextRange().getEndOffset() - 1);
          final PsiElement commonParent = startElement != null && endElement != null ? PsiTreeUtil.findCommonParent(startElement, endElement) : null;
          ASTNode node = null;
          if (commonParent != null) {
            node = commonParent.getNode();
          }
          if (node == null) {
            node = file.getNode();
          }
          for (FormatTextRanges.FormatTextRange range : ranges.getRanges()) {
            TextRange rangeToUse = preprocess(node, range.getTextRange());
            range.setTextRange(rangeToUse);
          }
          if (doPostponedFormatting) {
            RangeMarker[] markers = new RangeMarker[textRanges.size()];
            int i = 0;
            for (FormatTextRanges.FormatTextRange range : textRanges) {
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
            FormattingProgressTask.FORMATTING_CANCELLED_FLAG.set(false);
            component.doPostponedFormatting(file.getViewProvider());
            i = 0;
            for (FormatTextRanges.FormatTextRange range : textRanges) {
              RangeMarker marker = markers[i];
              if (marker != null) {
                range.setTextRange(TextRange.create(marker));
                marker.dispose();
              }
              i++;
            }
          }
          if (FormattingProgressTask.FORMATTING_CANCELLED_FLAG.get()) {
            return;
          }

          final FormattingModel originalModel = CoreFormatterUtil.buildModel(builder, file, mySettings, FormattingMode.REFORMAT);
          final FormattingModel model = new DocumentBasedFormattingModel(originalModel.getRootBlock(),
                                                                         document,
                                                                         project, mySettings, file.getFileType(), file);

          FormatterEx formatter = FormatterEx.getInstanceEx();
          if (CodeStyleManager.getInstance(project).isSequentialProcessingAllowed()) {
            formatter.setProgressTask(new FormattingProgressTask(project, file, document));
          }
          formatter.format(model, mySettings, mySettings.getIndentOptions(file.getFileType()), ranges);
          for (FormatTextRanges.FormatTextRange range : textRanges) {
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

  private static TextRange preprocess(@NotNull final ASTNode node, @NotNull TextRange range) {
    TextRange result = range;
    PsiElement psi = node.getPsi();
    if (!psi.isValid()) {
      for(PreFormatProcessor processor: Extensions.getExtensions(PreFormatProcessor.EP_NAME)) {
        result = processor.process(node, result);
      }
      return result;
    }
    
    PsiFile file = psi.getContainingFile();
    
    // We use a set here because we encountered a situation when more than one PSI leaf points to the same injected fragment
    // (at least for sql injected into sql).
    final LinkedHashSet<TextRange> injectedFileRangesSet = ContainerUtilRt.newLinkedHashSet();
    if (InjectedLanguageUtil.areInjectionsProcessed(file)) {
      for (DocumentWindow window : InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
        injectedFileRangesSet.add(TextRange.create(window.injectedToHost(0), window.injectedToHost(window.getTextLength())));
      }
    }
    else if (!file.getProject().isDefault()) {
      PsiLanguageInjectionHost.InjectedPsiVisitor visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          for (PsiLanguageInjectionHost.Shred place : places) {
            Segment rangeMarker = place.getHostRangeMarker();
            injectedFileRangesSet.add(TextRange.create(rangeMarker.getStartOffset(), rangeMarker.getEndOffset()));
          }
        }
      };

      for (PsiElement e = PsiTreeUtil.getDeepestFirst(file); e != null; e = PsiTreeUtil.nextLeaf(e, true)) {
        InjectedLanguageUtil.enumerate(e, visitor);
      }
    }
    
    if (!injectedFileRangesSet.isEmpty()) {
      List<TextRange> ranges = ContainerUtilRt.newArrayList(injectedFileRangesSet);
      Collections.reverse(ranges);
      for (TextRange injectedFileRange : ranges) {
        int startHostOffset = injectedFileRange.getStartOffset();
        int endHostOffset = injectedFileRange.getEndOffset();
        if (startHostOffset >= range.getStartOffset() && endHostOffset <= range.getEndOffset()) {
          PsiFile injected = InjectedLanguageUtil.findInjectedPsiNoCommit(file, startHostOffset);
          if (injected != null) {
            int startInjectedOffset = range.getStartOffset() > startHostOffset ? startHostOffset - range.getStartOffset() : 0;
            int endInjectedOffset = injected.getTextLength();
            if (range.getEndOffset() < endHostOffset) {
              endInjectedOffset -= endHostOffset - range.getEndOffset();
            }
            final TextRange initialInjectedRange = TextRange.create(startInjectedOffset, endInjectedOffset);
            TextRange injectedRange = initialInjectedRange;
            for (PreFormatProcessor processor : Extensions.getExtensions(PreFormatProcessor.EP_NAME)) {
              injectedRange = processor.process(injected.getNode(), injectedRange);
            }

            // Allow only range expansion (not reduction) for injected context.
            if ((initialInjectedRange.getStartOffset() > injectedRange.getStartOffset() && initialInjectedRange.getStartOffset() > 0)
                || (initialInjectedRange.getEndOffset() < injectedRange.getEndOffset()
                    && initialInjectedRange.getEndOffset() < injected.getTextLength()))
            {
              range = TextRange.create(
                range.getStartOffset() + injectedRange.getStartOffset() - initialInjectedRange.getStartOffset(),
                range.getEndOffset() + initialInjectedRange.getEndOffset() - injectedRange.getEndOffset());
            }
          }
        }
      }
    }
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
  private void wrapLongLinesIfNecessary(@NotNull final PsiFile file, @Nullable final Document document, final int startOffset,
                                        final int endOffset)
  {
    if (!mySettings.getCommonSettings(file.getLanguage()).WRAP_LONG_LINES ||
        PostprocessReformattingAspect.getInstance(file.getProject()).isViewProviderLocked(file.getViewProvider()) ||
        document == null) {
      return;
    }

    final VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    if ((vFile == null || vFile instanceof LightVirtualFile) && !ApplicationManager.getApplication().isUnitTestMode()) {
      // we assume that control flow reaches this place when the document is backed by a "virtual" file so any changes made by
      // a formatter affect only PSI and it is out of sync with a document text
      return;
    }

    Editor editor = PsiUtilBase.findEditor(file);
    EditorFactory editorFactory = null;
    if (editor == null) {
      if (!ApplicationManager.getApplication().isDispatchThread()) {
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
          final CaretModel caretModel = editorToUse.getCaretModel();
          final int caretOffset = caretModel.getOffset();
          final RangeMarker caretMarker = editorToUse.getDocument().createRangeMarker(caretOffset, caretOffset);
          doWrapLongLinesIfNecessary(editorToUse, file.getProject(), editorToUse.getDocument(), startOffset, endOffset);
          if (caretMarker.isValid() && caretModel.getOffset() != caretMarker.getStartOffset()) {
            caretModel.moveToOffset(caretMarker.getStartOffset());
          } 
        }
      });
    }
    finally {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
      if (documentManager.isUncommited(document)) documentManager.commitDocument(document);
      if (editorFactory != null) {
        editorFactory.releaseEditor(editor);
      }
    }
  }

  private void doWrapLongLinesIfNecessary(@NotNull final Editor editor, @NotNull final Project project, @NotNull Document document, 
                                          int startOffset, int endOffset) {
    // Normalization.
    int startOffsetToUse = Math.min(document.getTextLength(), Math.max(0, startOffset));
    int endOffsetToUse = Math.min(document.getTextLength(), Math.max(0, endOffset));

    LineWrapPositionStrategy strategy = LanguageLineWrapPositionStrategy.INSTANCE.forEditor(editor);
    CharSequence text = document.getCharsSequence();
    int startLine = document.getLineNumber(startOffsetToUse);
    int endLine = document.getLineNumber(Math.max(0, endOffsetToUse - 1));
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
      for (int i = startLineOffset; i < Math.min(endLineOffset, endOffsetToUse); i++) {
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

      boolean wrapLine = false; 
      int preferredWrapPosition = Integer.MAX_VALUE;
      if (!hasTabs) {
        if (Math.min(endLineOffset, endOffsetToUse) - startLineOffset > mySettings.RIGHT_MARGIN) {
          preferredWrapPosition = startLineOffset + mySettings.RIGHT_MARGIN - FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS;
          wrapLine = true;
        }
      }
      else if (canOptimize) {
        int width = 0;
        int symbolWidth;
        for (int i = startLineOffset; i < Math.min(endLineOffset, endOffsetToUse); i++) {
          char c = text.charAt(i);
          switch (c) {
            case '\t': symbolWidth = tabSize - (width % tabSize); break;
            default: symbolWidth = 1;
          }
          if (width + symbolWidth + FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS >= mySettings.RIGHT_MARGIN
              && (Math.min(endLineOffset, endOffsetToUse) - i) >= FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS)
          {
            // Remember preferred position.
            preferredWrapPosition = i - 1;
          }
          if (width + symbolWidth >= mySettings.RIGHT_MARGIN) {
            wrapLine = true;
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
        for (int i = startLineOffset; i < Math.min(endLineOffset, endOffsetToUse); i++) {
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
              && (Math.min(endLineOffset, endOffsetToUse) - i) >= FormatConstants.RESERVED_LINE_WRAP_WIDTH_IN_COLUMNS)
          {
            preferredWrapPosition = i - 1;
          }
          if (width + symbolWidth >= mySettings.RIGHT_MARGIN) {
            wrapLine = true;
            break;
          }
          x = newX;
          width += symbolWidth;
        }
      }
      if (!wrapLine || preferredWrapPosition >= endLineOffset) {
        continue;
      }
      if (preferredWrapPosition >= endOffsetToUse) {
        return;
      }

      // We know that current line exceeds right margin if control flow reaches this place, so, wrap it.
      int wrapOffset = strategy.calculateWrapPosition(
        document, editor.getProject(), Math.max(startLineOffset, startOffsetToUse), Math.min(endLineOffset, endOffsetToUse),
        preferredWrapPosition, false, false
      );
      if (wrapOffset < 0) {
        continue;
      }
      editor.getCaretModel().moveToOffset(wrapOffset);
      
      // There is a possible case that formatting is performed from project view and editor is not opened yet. The problem is that
      // its data context doesn't contain information about project then. So, we explicitly support that here (see IDEA-72791).
      final DataContext baseDataContext = DataManager.getInstance().getDataContext(editor.getComponent());
      final DataContext dataContext = new DelegatingDataContext(baseDataContext) {
        @Override
        public Object getData(@NonNls String dataId) {
          Object result = baseDataContext.getData(dataId);
          if (result == null && PlatformDataKeys.PROJECT.is(dataId)) {
            result = project;
          } 
          return result;
        }
      };
      

      SelectionModel selectionModel = editor.getSelectionModel();
      int startSelectionOffset = 0;
      int endSelectionOffset = 0;
      boolean restoreSelection = selectionModel.hasSelection();
      if (restoreSelection) {
        startSelectionOffset = selectionModel.getSelectionStart();
        endSelectionOffset = selectionModel.getSelectionEnd();
        selectionModel.removeSelection();
      }
      int textLengthBeforeWrap = document.getTextLength();

      DataManager.getInstance().saveInDataContext(dataContext, WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY, true);
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      try {
        Runnable command = new Runnable() {
          @Override
          public void run() {
            EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, dataContext);
          }
        };
        if (commandProcessor.getCurrentCommand() == null) {
          commandProcessor.executeCommand(editor.getProject(), command, WRAP_LINE_COMMAND_NAME, null);
        }
        else {
          command.run();
        }
      }
      finally {
        DataManager.getInstance().saveInDataContext(dataContext, WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY, null);
      }
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

      // We know that number of lines is just increased, hence, update the data accordingly.
      maxLine++;
    }
  }
  
  private static class DelegatingDataContext implements DataContext, UserDataHolder {
    
    private final DataContext myDataContextDelegate;
    private final UserDataHolder myDataHolderDelegate;

    DelegatingDataContext(DataContext delegate) {
      myDataContextDelegate = delegate;
      if (delegate instanceof UserDataHolder) {
        myDataHolderDelegate = (UserDataHolder)delegate;
      }
      else {
        myDataHolderDelegate = null;
      }
    }

    @Override
    public Object getData(@NonNls String dataId) {
      return myDataContextDelegate.getData(dataId);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myDataHolderDelegate == null ? null : myDataHolderDelegate.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      if (myDataHolderDelegate != null) {
        myDataHolderDelegate.putUserData(key, value);
      }
    }
  }
}

