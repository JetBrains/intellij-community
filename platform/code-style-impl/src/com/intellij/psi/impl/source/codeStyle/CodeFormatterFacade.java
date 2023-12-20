// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.codeStyle;

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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
import com.intellij.util.text.TextRangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CodeFormatterFacade {

  private static final Logger LOG = Logger.getInstance(CodeFormatterFacade.class);

  private final CodeStyleSettings mySettings;
  private final FormatterTagHandler myTagHandler;
  private final boolean myCanChangeWhitespaceOnly;

  public static final ThreadLocal<Boolean> FORMATTING_CANCELLED_FLAG = ThreadLocal.withInitial(() -> false);

  public CodeFormatterFacade(CodeStyleSettings settings, @Nullable Language language) {
    this(settings, language, false);
  }

  public CodeFormatterFacade(CodeStyleSettings settings,
                             @Nullable Language language,
                             boolean canChangeWhitespaceOnly) {
    mySettings = settings;
    myTagHandler = new FormatterTagHandler(settings);
    myCanChangeWhitespaceOnly = canChangeWhitespaceOnly;
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
    final boolean delegateToTopLevel = shouldDelegateToTopLevel(document, file);

    PsiElement elementToFormat = delegateToTopLevel ? InjectedLanguageManager
      .getInstance(file.getProject()).getTopLevelFile(file) : psiElement;
    final PsiFile fileToFormat = elementToFormat.getContainingFile();


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
      RangeMarker rangeMarker = null;
      CodeFormattingData codeFormattingData = CodeFormattingData.getOrCreate(fileToFormat);

      try {
        if (document != null && endOffset < document.getTextLength()) {
          rangeMarker = document.createRangeMarker(startOffset, endOffset);
        }

        TextRange range = preprocess(codeFormattingData, element, TextRange.create(startOffset, endOffset));
        if (delegateToTopLevel) {
          range = ((DocumentWindow)document).injectedToHost(range);
        }

        final FormattingModel model = CoreFormatterUtil.buildModel(builder, elementToFormat, range, mySettings, FormattingMode.REFORMAT);
        if (file.getTextLength() > 0) {
          try {
            final FormatTextRanges ranges = new FormatTextRanges(range, true);
            setDisabledRanges(fileToFormat, ranges);
            FormatterEx.getInstanceEx().format(
              model, mySettings, getIndentOptions(mySettings, file.getProject(), file, document, range), ranges
            );
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
          }
          else {
            assert false;
          }
        }
      }
      finally {
        if (rangeMarker != null) {
          rangeMarker.dispose();
        }
        codeFormattingData.dispose();
      }
    }

    return element;
  }

  public void processText(@NotNull PsiFile file, final FormatTextRanges ranges, boolean doPostponedFormatting) {
    final Project project = file.getProject();
    Document document = file.getViewProvider().getDocument();
    final List<FormatTextRange> textRanges = ranges.getRanges();
    if (document instanceof DocumentWindow documentWindow && shouldDelegateToTopLevel(file)) {
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
      for (FormatTextRange range : textRanges) {
        range.setTextRange(documentWindow.injectedToHost(range.getTextRange()));
      }
      document = documentWindow.getDelegate();
    }

    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder != null) {
      if (file.getTextLength() > 0) {
        LOG.assertTrue(document != null);
        if (ranges.isExtendToContext()) {
          ranges.setExtendedRanges(new ContextFormattingRangesExtender(document, file).getExtendedRanges(ranges.getTextRanges()));
        }
        CodeFormattingData formattingData = CodeFormattingData.getOrCreate(file);
        try {
          ASTNode containingNode = findContainingNode(file, ranges.getBoundRange());
          if (containingNode != null) {
            for (FormatTextRange range : ranges.getRanges()) {
              TextRange rangeToUse = preprocess(formattingData, containingNode, range.getTextRange());
              range.setTextRange(rangeToUse);
            }
          }
          if (doPostponedFormatting) {
            invokePostponedFormatting(file, document, textRanges);
          }
          if (FORMATTING_CANCELLED_FLAG.get()) {
            return;
          }

          TextRange formattingModelRange = ObjectUtils.notNull(ranges.getBoundRange(), file.getTextRange());

          final FormattingModel originalModel =
            CoreFormatterUtil.buildModel(builder, file, formattingModelRange, mySettings, FormattingMode.REFORMAT);
          final FormattingModel model = new DocumentBasedFormattingModel(originalModel,
                                                                         document,
                                                                         project, mySettings, file.getFileType(), file);

          FormatterEx formatter = FormatterEx.getInstanceEx();
          if (CodeStyleManager.getInstance(project).isSequentialProcessingAllowed()) {
            FormattingProgressCallback progressCallback =
              FormattingProgressCallbackFactory.getInstance().createProgressCallback(project, file, document);
            if (progressCallback != null) {
              formatter.setProgressTask(progressCallback);
            }
          }

          CommonCodeStyleSettings.IndentOptions indentOptions =
            getIndentOptions(mySettings, project, file, document, textRanges.size() == 1 ? textRanges.get(0).getTextRange() : null);
          setDisabledRanges(file, ranges);
          formatter.format(model, mySettings, indentOptions, ranges);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        finally {
          formattingData.dispose();
        }
      }
    }
  }

  static @NotNull CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings,
                                                                         @NotNull Project project,
                                                                         @NotNull PsiFile psiFile,
                                                                         @Nullable Document document,
                                                                         @Nullable TextRange textRange) {
    VirtualFile virtualFile = getVirtualFile(psiFile, document);
    return virtualFile != null ?
           settings.getIndentOptionsByFile(project, virtualFile, textRange) :
           settings.getIndentOptions(psiFile.getFileType());
  }

  private static @Nullable VirtualFile getVirtualFile(@NotNull PsiFile psiFile, @Nullable Document document) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      return file;
    }
    if (document != null) {
      return FileDocumentManager.getInstance().getFile(document);
    }
    return null;
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
    FORMATTING_CANCELLED_FLAG.set(false);
    component.doPostponedFormatting(file.getViewProvider());
    i = 0;
    for (FormatTextRange range : textRanges) {
      RangeMarker marker = markers[i];
      if (marker != null) {
        range.setTextRange(marker.getTextRange());
        marker.dispose();
      }
      i++;
    }
  }

  static @Nullable ASTNode findContainingNode(@NotNull PsiFile file, @Nullable TextRange range) {
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

  private TextRange preprocess(@NotNull CodeFormattingData formattingData, final @NotNull ASTNode node, @NotNull TextRange range) {
    TextRange result = range;
    PsiElement psi = node.getPsi();
    if (!psi.isValid()) {
      return result;
    }

    PsiFile file = psi.getContainingFile();

    final Set<TextRange> injectedFileRangesSet = formattingData.getInjectedRanges(range);

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
                    && initialInjectedRange.getEndOffset() < injected.getTextLength())) {
              range = TextRange.create(
                range.getStartOffset() + injectedRange.getStartOffset() - initialInjectedRange.getStartOffset(),
                range.getEndOffset() + initialInjectedRange.getEndOffset() - injectedRange.getEndOffset());
            }
          }
        }
      }
    }

    if (!mySettings.FORMATTER_TAGS_ENABLED) {
      for (PreFormatProcessor processor : PreFormatProcessor.EP_NAME.getExtensionList()) {
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

  private TextRange preprocessEnabledRanges(final @NotNull ASTNode node, @NotNull TextRange range) {
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


  static boolean shouldDelegateToTopLevel(@NotNull PsiFile file) {
    for (var provider: InjectedFormattingOptionsProvider.EP_NAME.getExtensions()) {
      var result = provider.shouldDelegateToTopLevel(file);
      if (result == null) continue;
      return result;
    }
    return true;
  }

  static boolean shouldDelegateToTopLevel(Document document, @NotNull PsiFile file) {
    return document instanceof DocumentWindow && shouldDelegateToTopLevel(file);
  }
}

