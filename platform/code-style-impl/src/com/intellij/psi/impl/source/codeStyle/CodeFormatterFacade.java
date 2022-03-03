// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.*;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.VirtualFormattingListener;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.util.EditorFacade;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import com.intellij.util.text.TextRangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CodeFormatterFacade {

  private static final Logger LOG = Logger.getInstance(CodeFormatterFacade.class);

  private final CodeStyleSettings mySettings;
  private final FormatterTagHandler myTagHandler;
  private final int myRightMargin;
  private final boolean myCanChangeWhitespaceOnly;
  private final EditorFacade myEditorFacade;

  public CodeFormatterFacade(CodeStyleSettings settings, @Nullable Language language) {
    this(settings, language, false);
  }

  public CodeFormatterFacade(CodeStyleSettings settings,
                             @Nullable Language language,
                             boolean canChangeWhitespaceOnly) {
    mySettings = settings;
    myTagHandler = new FormatterTagHandler(settings);
    myRightMargin = mySettings.getRightMargin(language);
    myCanChangeWhitespaceOnly = canChangeWhitespaceOnly;
    myEditorFacade = EditorFacade.getInstance();
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

    PsiElement elementToFormat = document instanceof DocumentWindow ? InjectedLanguageManager
          .getInstance(file.getProject()).getTopLevelFile(file) : psiElement;
    final PsiFile fileToFormat = elementToFormat.getContainingFile();

    RangeMarker rangeMarker = null;

    // Dirty workaround
    // In case we're formatting not the original file, we have to keep the formatting listener
    // if any and drop it after creating a VirtualFormattingModel.
    VirtualFormattingListener listener = VirtualFormattingImplKt.getVirtualFormattingListener(file);
    final FormattingModelBuilder builder;
    try {
      if (listener != null) {
        VirtualFormattingImplKt.setVirtualFormattingListener(fileToFormat, listener);
      }
      builder = LanguageFormatting.INSTANCE.forContext(fileToFormat);
    }
    finally {
      if (listener != null) {
        VirtualFormattingImplKt.setVirtualFormattingListener(fileToFormat, null);
      }
    }
    // End of dirty workaround

    if (builder != null) {
      if (document != null && endOffset < document.getTextLength()) {
        rangeMarker = document.createRangeMarker(startOffset, endOffset);
      }

      TextRange range = preprocess(element, TextRange.create(startOffset, endOffset));
      if (document instanceof DocumentWindow) {
        DocumentWindow documentWindow = (DocumentWindow)document;
        range = documentWindow.injectedToHost(range);
      }

      //final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
      final FormattingModel model = CoreFormatterUtil.buildModel(builder, elementToFormat, range, mySettings, FormattingMode.REFORMAT);
      if (file.getTextLength() > 0) {
        try {
          final FormatTextRanges ranges = new FormatTextRanges(range, true);
          setDisabledRanges(fileToFormat,ranges);
          FormatterEx.getInstanceEx().format(
            model, mySettings, mySettings.getIndentOptionsByFile(fileToFormat, range), ranges
          );

          wrapLongLinesIfNecessary(file, document, startOffset, endOffset, myRightMargin);
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
          rangeMarker.dispose();
          return result.getNode();
        } else {
          assert false;
        }
      }
//      return SourceTreeToPsiMap.psiElementToTree(pointer.getElement());
    }

    if (rangeMarker != null) {
      rangeMarker.dispose();
    }
    return element;
  }

  public void processText(@NotNull PsiFile file, final FormatTextRanges ranges, boolean doPostponedFormatting) {
    final Project project = file.getProject();
    Document document = file.getViewProvider().getDocument();
    final List<FormatTextRange> textRanges = ranges.getRanges();
    if (document instanceof DocumentWindow) {
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
      final DocumentWindow documentWindow = (DocumentWindow)document;
      for (FormatTextRange range : textRanges) {
        range.setTextRange(documentWindow.injectedToHost(range.getTextRange()));
      }
      document = documentWindow.getDelegate();
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder != null) {
      if (file.getTextLength() > 0) {
        LOG.assertTrue(document != null);
        ranges.setExtendedRanges(new FormattingRangesExtender(document, file).getExtendedRanges(ranges.getTextRanges()));
        try {
          ASTNode containingNode = findContainingNode(file, ranges.getBoundRange());
          if (containingNode != null) {
            for (FormatTextRange range : ranges.getRanges()) {
              TextRange rangeToUse = preprocess(containingNode, range.getTextRange());
              range.setTextRange(rangeToUse);
            }
          }
          if (doPostponedFormatting) {
            invokePostponedFormatting(file, document, textRanges);
          }
          if (FormattingProgressTask.FORMATTING_CANCELLED_FLAG.get()) {
            return;
          }

          TextRange formattingModelRange = ObjectUtils.notNull(ranges.getBoundRange(), file.getTextRange());

          final FormattingModel originalModel = CoreFormatterUtil.buildModel(builder, file, formattingModelRange, mySettings, FormattingMode.REFORMAT);
          final FormattingModel model = new DocumentBasedFormattingModel(originalModel,
                                                                         document,
                                                                         project, mySettings, file.getFileType(), file);

          FormatterEx formatter = FormatterEx.getInstanceEx();
          if (CodeStyleManager.getInstance(project).isSequentialProcessingAllowed()) {
            formatter.setProgressTask(new FormattingProgressTask(project, file, document));
          }

          CommonCodeStyleSettings.IndentOptions indentOptions =
            mySettings.getIndentOptionsByFile(file, textRanges.size() == 1 ? textRanges.get(0).getTextRange() : null);

          setDisabledRanges(file, ranges);
          formatter.format(model, mySettings, indentOptions, ranges);
          for (FormatTextRange range : textRanges) {
            TextRange textRange = range.getTextRange();
            wrapLongLinesIfNecessary(file, document, textRange.getStartOffset(), textRange.getEndOffset(), myRightMargin);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void setDisabledRanges(@NotNull PsiFile file, FormatTextRanges ranges) {
    final Iterable<TextRange> excludedRangesIterable = TextRangeUtil.excludeRanges(
      file.getTextRange(), myTagHandler.getEnabledRanges(file.getNode(), file.getTextRange()));
    ranges.setDisabledRanges((Collection<TextRange>)excludedRangesIterable);
  }

  private static void invokePostponedFormatting(@NotNull PsiFile file,
                                                Document document,
                                                List<? extends FormatTextRange> textRanges) {
    RangeMarker[] markers = new RangeMarker[textRanges.size()];
    int i = 0;
    for (FormatTextRange range : textRanges) {
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

    PostprocessReformattingAspect component = PostprocessReformattingAspect.getInstance(file.getProject());
    FormattingProgressTask.FORMATTING_CANCELLED_FLAG.set(false);
    component.doPostponedFormatting(file.getViewProvider());
    i = 0;
    for (FormatTextRange range : textRanges) {
      RangeMarker marker = markers[i];
      if (marker != null) {
        range.setTextRange(TextRange.create(marker));
        marker.dispose();
      }
      i++;
    }
  }

  @Nullable
  static ASTNode findContainingNode(@NotNull PsiFile file, @Nullable TextRange range) {
    Language language = file.getLanguage();
    if (range == null) return null;
    final FileViewProvider viewProvider = file.getViewProvider();
    final PsiElement startElement = viewProvider.findElementAt(range.getStartOffset(), language);
    final PsiElement endElement = viewProvider.findElementAt(range.getEndOffset() - 1, language);
    final PsiElement commonParent = startElement != null && endElement != null ?
                                    PsiTreeUtil.findCommonParent(startElement, endElement) :
                                    null;
    ASTNode node = null;
    if (commonParent != null) {
      node = commonParent.getNode();
      // Find the topmost parent with the same range.
      ASTNode parent = node.getTreeParent();
      while (parent != null && parent.getTextRange().equals(commonParent.getTextRange())) {
        node = parent;
        parent = parent.getTreeParent();
      }
    }
    if (node == null) {
      node = file.getNode();
    }
    return node;
  }

  private TextRange preprocess(@NotNull final ASTNode node, @NotNull TextRange range) {
    TextRange result = range;
    PsiElement psi = node.getPsi();
    if (!psi.isValid()) {
      return result;
    }

    PsiFile file = psi.getContainingFile();

    // We use a set here because we encountered a situation when more than one PSI leaf points to the same injected fragment
    // (at least for sql injected into sql).
    final LinkedHashSet<TextRange> injectedFileRangesSet = new LinkedHashSet<>();

    if (!psi.getProject().isDefault()) {
      List<DocumentWindow> injectedDocuments = InjectedLanguageManager.getInstance(file.getProject()).getCachedInjectedDocumentsInRange(file, file.getTextRange());
      if (!injectedDocuments.isEmpty()) {
        for (DocumentWindow injectedDocument : injectedDocuments) {
          injectedFileRangesSet.add(TextRange.from(injectedDocument.injectedToHost(0), injectedDocument.getTextLength()));
        }
      }
      else {
        Collection<PsiLanguageInjectionHost> injectionHosts = collectInjectionHosts(file, range);
        PsiLanguageInjectionHost.InjectedPsiVisitor visitor = (injectedPsi, places) -> {
          for (PsiLanguageInjectionHost.Shred place : places) {
            Segment rangeMarker = place.getHostRangeMarker();
            injectedFileRangesSet.add(TextRange.create(rangeMarker.getStartOffset(), rangeMarker.getEndOffset()));
          }
        };
        for (PsiLanguageInjectionHost host : injectionHosts) {
          InjectedLanguageManager.getInstance(file.getProject()).enumerate(host, visitor);
        }
      }
    }

    if (!injectedFileRangesSet.isEmpty()) {
      List<TextRange> ranges = new ArrayList<>(injectedFileRangesSet);
      Collections.reverse(ranges);
      for (TextRange injectedFileRange : ranges) {
        int startHostOffset = injectedFileRange.getStartOffset();
        int endHostOffset = injectedFileRange.getEndOffset();
        if (startHostOffset >= range.getStartOffset() && endHostOffset <= range.getEndOffset()) {
          PsiFile injected = InjectedLanguageUtilBase.findInjectedPsiNoCommit(file, startHostOffset);
          if (injected != null) {
            final TextRange initialInjectedRange = TextRange.create(0, injected.getTextLength());
            TextRange injectedRange = initialInjectedRange;
            for (PreFormatProcessor processor : PreFormatProcessor.EP_NAME.getExtensionList()) {
              if (processor.changesWhitespacesOnly() || !myCanChangeWhitespaceOnly) {
                injectedRange = processor.process(injected.getNode(), injectedRange);
              }
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

    if (!mySettings.FORMATTER_TAGS_ENABLED) {
      for (PreFormatProcessor processor: PreFormatProcessor.EP_NAME.getExtensionList()) {
        if (processor.changesWhitespacesOnly() || !myCanChangeWhitespaceOnly) {
          result = processor.process(node, result);
        }
      }
    }
    else {
      result = preprocessEnabledRanges(node, result);
    }

    return result;
  }

  private TextRange preprocessEnabledRanges(@NotNull final ASTNode node, @NotNull TextRange range) {
    TextRange result = TextRange.create(range.getStartOffset(), range.getEndOffset());
    List<TextRange> enabledRanges = myTagHandler.getEnabledRanges(node, result);
    int delta = 0;
    for (TextRange enabledRange : enabledRanges) {
      enabledRange = enabledRange.shiftRight(delta);
      for (PreFormatProcessor processor : PreFormatProcessor.EP_NAME.getExtensionList()) {
        if (processor.changesWhitespacesOnly() || !myCanChangeWhitespaceOnly) {
          TextRange processedRange = processor.process(node, enabledRange);
          delta += processedRange.getLength() - enabledRange.getLength();
        }
      }
    }
    result = result.grown(delta);
    return result;
  }

  @NotNull
  private static Collection<PsiLanguageInjectionHost> collectInjectionHosts(@NotNull PsiFile file, @NotNull TextRange range) {
    Stack<PsiElement> toProcess = new Stack<>();
    for (PsiElement e = file.findElementAt(range.getStartOffset()); e != null; e = e.getNextSibling()) {
      if (e.getTextRange().getStartOffset() >= range.getEndOffset()) {
        break;
      }
      toProcess.push(e);
    }
    if (toProcess.isEmpty()) {
      return Collections.emptySet();
    }
    Set<PsiLanguageInjectionHost> result = null;
    while (!toProcess.isEmpty()) {
      PsiElement e = toProcess.pop();
      if (e instanceof PsiLanguageInjectionHost) {
        if (result == null) {
          result = new HashSet<>();
        }
        result.add((PsiLanguageInjectionHost)e);
      }
      else {
        for (PsiElement child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (e.getTextRange().getStartOffset() >= range.getEndOffset()) {
            break;
          }
          toProcess.push(child);
        }
      }
    }
    return result == null ? Collections.emptySet() : result;
  }


  /**
   * Inspects all lines of the given document and wraps all of them that exceed {@link CodeStyleSettings#getRightMargin(Language)}
   * right margin}.
   * <p/>
   * I.e. the algorithm is to do the following for every line:
   * <p/>
   * <pre>
   * <ol>
   *   <li>
   *      Check if the line exceeds {@link CodeStyleSettings#getRightMargin(Language)}  right margin}. Go to the next line in the case of
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
                                        final int endOffset, final int rightMargin)
  {
    if (!mySettings.getCommonSettings(file.getLanguage()).WRAP_LONG_LINES ||
        PostprocessReformattingAspect.getInstance(file.getProject()).isViewProviderLocked(file.getViewProvider()) ||
        document == null) {
      return;
    }

    FormatterTagHandler formatterTagHandler = new FormatterTagHandler(CodeStyle.getSettings(file));
    List<TextRange> enabledRanges = formatterTagHandler.getEnabledRanges(file.getNode(), new TextRange(startOffset, endOffset));

    myEditorFacade.wrapLongLinesIfNecessary(file, document, startOffset, endOffset, enabledRanges, rightMargin);
  }
}

