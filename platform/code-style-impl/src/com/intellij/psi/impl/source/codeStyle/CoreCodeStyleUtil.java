// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.diagnostic.PluginException;
import com.intellij.formatting.FormatterTagHandler;
import com.intellij.formatting.FormattingRangesInfo;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class CoreCodeStyleUtil {
  private static final Logger LOG = Logger.getInstance(CoreCodeStyleUtil.class);

  private static final ThreadLocal<ProcessingUnderProgressInfo> SEQUENTIAL_PROCESSING_ALLOWED
    = ThreadLocal.withInitial(() -> new ProcessingUnderProgressInfo());

  private CoreCodeStyleUtil() {
  }

  public static PsiElement postProcessElement(@NotNull PsiFile file, final @NotNull PsiElement element, boolean isWhitespaceOnly) {
    CodeStyleSettings settingsForFile = CodeStyle.getSettings(file);
    List<TextRange> textRanges;
    if (settingsForFile.FORMATTER_TAGS_ENABLED) {
      FormatterTagHandler tagHandler = new FormatterTagHandler(settingsForFile);
      textRanges = tagHandler.getEnabledRanges(file.getNode(), file.getTextRange());
    }
    else {
      textRanges = Collections.singletonList(element.getTextRange());
    }
    for (TextRange range : textRanges) {
      if (range.contains(element.getTextRange())) {
        PsiElement currElement = element;
        for (PostFormatProcessor postFormatProcessor : getPostProcessors(isWhitespaceOnly)) {
          if (currElement == null) break;
          currElement = processElementOrFail(postFormatProcessor, currElement, settingsForFile);
        }
        return currElement;
      }
      else if (range.intersects(element.getTextRange())) {
        postProcessRange(file, element.getTextRange().intersection(range), settingsForFile, isWhitespaceOnly);
      }
    }
    return element;
  }

  private static @Nullable PsiElement processElementOrFail(@NotNull PostFormatProcessor processor,
                                                           @NotNull PsiElement element,
                                                           @NotNull CodeStyleSettings settings) {
    PsiElement result = element;
    try {
      result = processor.processElement(result, settings);
      if (!result.isValid()) {
        LOG.error(new RuntimeExceptionWithAttachments(String.format("PSI crash detected: processor=%s, result=%s", processor,
                                                                    result), new Attachment("text", result.getText())));
        return null;
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(PluginException.createByClass(e, processor.getClass()));
    }
    return result;
  }

  private static @Unmodifiable Collection<PostFormatProcessor> getPostProcessors(boolean isWhitespaceOnly) {
    if (isWhitespaceOnly) {
      return ContainerUtil.filter(PostFormatProcessor.EP_NAME.getExtensionList(), processor -> processor.isWhitespaceOnly());
    }
    else {
      return PostFormatProcessor.EP_NAME.getExtensionList();
    }
  }

  public static List<RangeFormatInfo> getRangeFormatInfoList(@NotNull PsiFile file, @NotNull FormattingRangesInfo ranges) {
    List<RangeFormatInfo> infos = new ArrayList<>();
    for (TextRange range : ranges.getTextRanges()) {
      infos.add(new RangeFormatInfo(file, range));
    }
    return infos;
  }

  public static void postProcessRanges(@NotNull List<? extends RangeFormatInfo> rangeFormatInfoList,
                                       @NotNull Consumer<? super TextRange> postProcessFormatter) {
    for (RangeFormatInfo info : rangeFormatInfoList) {
      int startOffset = info.getStartOffset();
      int endOffset = info.getEndOffset();
      if (startOffset >= 0 && endOffset >= 0 && endOffset > startOffset) {
        postProcessFormatter.accept(new TextRange(startOffset, endOffset));
      }
      info.disposePointers();
    }
  }

  public static void postProcessText(final @NotNull PsiFile file, final @NotNull TextRange textRange, boolean isWhitespaceOnly) {
    CodeStyleSettings settings = CodeStyle.getSettings(file);
    if (!getSettings(file).FORMATTER_TAGS_ENABLED) {
      postProcessRange(file, textRange, settings, isWhitespaceOnly);
    }
    else {
      postProcessEnabledRanges(file, textRange, settings, isWhitespaceOnly);
    }
  }

  private static void postProcessEnabledRanges(final @NotNull PsiFile file,
                                               @NotNull TextRange range,
                                               CodeStyleSettings settings,
                                               boolean isWhitespaceOnly) {
    List<TextRange> enabledRanges = new FormatterTagHandler(getSettings(file)).getEnabledRanges(file.getNode(), range);
    int delta = 0;
    for (TextRange enabledRange : enabledRanges) {
      enabledRange = enabledRange.shiftRight(delta);
      TextRange processedRange = postProcessRange(file, enabledRange, settings, isWhitespaceOnly);
      delta += processedRange.getLength() - enabledRange.getLength();
    }
  }

  private static TextRange postProcessRange(@NotNull PsiFile file, @NotNull TextRange textRange,
                                            @NotNull CodeStyleSettings settings, boolean isWhitespaceOnly) {
    TextRange currentRange = textRange;
    for (final PostFormatProcessor myPostFormatProcessor : getPostProcessors(isWhitespaceOnly)) {
      currentRange = myPostFormatProcessor.processText(file, currentRange, settings);
    }
    return currentRange;
  }

  public static final class RangeFormatInfo {
    private final PsiFile                   myFile;
    private final SmartPsiElementPointer<?> startPointer;
    private final SmartPsiElementPointer<?> endPointer;
    private final boolean                   fromStart;
    private final boolean                   toEnd;

    RangeFormatInfo(@NotNull PsiFile file, @NotNull TextRange range) {
      myFile = file;
      fromStart = range.getStartOffset() == 0;
      toEnd = range.getEndOffset() == file.getTextLength();
      startPointer = fromStart ? null : createPsiPointer(range.getStartOffset());
      endPointer = toEnd ? null : createPsiPointer(range.getEndOffset());
    }

    private @Nullable SmartPsiElementPointer<?> createPsiPointer(int offset) {
      PsiElement element = findElementInTreeWithFormatterEnabled(myFile, offset);
      if (element != null) {
        if (!element.isValid()) {
          LOG.error("Invalid element " + element + "; file: " + myFile.getName());
        }
        return SmartPointerManager.getInstance(myFile.getProject()).createSmartPsiElementPointer(element);
      }
      return null;
    }

    private int getStartOffset() {
      if (fromStart) return 0;
      TextRange range = getElementRange(startPointer);
      return range != null ? range.getStartOffset() : -1;
    }

    private int getEndOffset() {
      if (toEnd) return myFile.getTextLength();
      TextRange range = getElementRange(endPointer);
      return range != null ? range.getEndOffset() : -1;
    }

    private static @Nullable TextRange getElementRange(@Nullable SmartPsiElementPointer<?> pointer) {
      return pointer != null ?
             ObjectUtils.doIfNotNull(pointer.getElement(), element -> element.getTextRange()) : null;
    }

    private void disposePointers() {
      SmartPointerManager pointerManager = SmartPointerManager.getInstance(myFile.getProject());
      if (startPointer != null) {
        pointerManager.removePointer(startPointer);
      }
      if (endPointer != null) {
        pointerManager.removePointer(endPointer);
      }
    }
  }

  public static @Nullable PsiElement findElementInTreeWithFormatterEnabled(final PsiFile file, final int offset) {
    final PsiElement bottommost = file.findElementAt(offset);
    if (bottommost != null && LanguageFormatting.INSTANCE.forContext(bottommost) != null) {
      return bottommost;
    }

    final Language fileLang = file.getLanguage();
    if (fileLang instanceof CompositeLanguage) {
      return file.getViewProvider().findElementAt(offset, fileLang);
    }

    return bottommost;
  }


  @ApiStatus.Internal
  public static void setSequentialProcessingAllowed(boolean allowed) {
    ProcessingUnderProgressInfo info = SEQUENTIAL_PROCESSING_ALLOWED.get();
    if (allowed) {
      info.decrement();
    }
    else {
      info.increment();
    }
  }

  static boolean isSequentialProcessingAllowed() {
    return SEQUENTIAL_PROCESSING_ALLOWED.get().isAllowed();
  }

  private static final class ProcessingUnderProgressInfo {

    private static final long DURATION_TIME = TimeUnit.SECONDS.toMillis(5);

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

  private static CodeStyleSettings getSettings(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file);
  }
}
