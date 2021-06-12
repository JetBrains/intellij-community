// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApiStatus.Internal
public class CoreCodeStyleUtil {
  private final static Logger LOG = Logger.getInstance(CoreCodeStyleUtil.class);

  private static final ThreadLocal<ProcessingUnderProgressInfo> SEQUENTIAL_PROCESSING_ALLOWED
    = ThreadLocal.withInitial(() -> new ProcessingUnderProgressInfo());

  private CoreCodeStyleUtil() {
  }

  public static PsiElement postProcessElement(@NotNull PsiFile file, @NotNull final PsiElement formatted) {
    PsiElement result = formatted;
    CodeStyleSettings settingsForFile = CodeStyle.getSettings(file);
    if (settingsForFile.FORMATTER_TAGS_ENABLED && formatted instanceof PsiFile) {
      postProcessEnabledRanges((PsiFile) formatted, formatted.getTextRange(), settingsForFile);
    }
    else {
      boolean brokenProcFound = false;
      for (PostFormatProcessor postFormatProcessor : PostFormatProcessor.EP_NAME.getExtensionList()) {
        try {
          result = postFormatProcessor.processElement(result, settingsForFile);
          if (!result.isValid() && !brokenProcFound) {
            LOG.error(new RuntimeExceptionWithAttachments(String.format("PSI crash detected: processor=%s, result=%s", postFormatProcessor,
                                                                        result), new Attachment("text", result.getText())));
            brokenProcFound = true;
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(PluginException.createByClass(e, postFormatProcessor.getClass()));
        }
      }
    }
    return result;
  }


  public static List<RangeFormatInfo> getRangeFormatInfoList(@NotNull PsiFile file, @NotNull FormattingRangesInfo ranges) {
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(file.getProject());

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
    return infos;
  }

  public static void postProcessRanges(@NotNull PsiFile file,
                                       @NotNull List<RangeFormatInfo> rangeFormatInfoList,
                                       @NotNull Consumer<TextRange> postProcessFormatter) {
    final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(file.getProject());
    for (RangeFormatInfo info : rangeFormatInfoList) {
      final PsiElement startElement = info.startPointer == null ? null : info.startPointer.getElement();
      final PsiElement endElement = info.endPointer == null ? null : info.endPointer.getElement();
      if ((startElement != null || info.fromStart) && (endElement != null || info.toEnd)) {
        TextRange currRange = new TextRange(info.fromStart ? 0 : startElement.getTextRange().getStartOffset(),
                                            info.toEnd ? file.getTextLength() : endElement.getTextRange().getEndOffset());
        postProcessFormatter.accept(currRange);
      }
      if (info.startPointer != null) smartPointerManager.removePointer(info.startPointer);
      if (info.endPointer != null) smartPointerManager.removePointer(info.endPointer);
    }
  }

  public static void postProcessText(@NotNull final PsiFile file, @NotNull final TextRange textRange) {
    if (!getSettings(file).FORMATTER_TAGS_ENABLED) {
      TextRange currentRange = textRange;
      for (final PostFormatProcessor myPostFormatProcessor : PostFormatProcessor.EP_NAME.getExtensionList()) {
        currentRange = myPostFormatProcessor.processText(file, currentRange, getSettings(file));
      }
    }
    else {
      postProcessEnabledRanges(file, textRange, getSettings(file));
    }
  }

  private static void postProcessEnabledRanges(@NotNull final PsiFile file, @NotNull TextRange range, CodeStyleSettings settings) {
    List<TextRange> enabledRanges = new FormatterTagHandler(getSettings(file)).getEnabledRanges(file.getNode(), range);
    int delta = 0;
    for (TextRange enabledRange : enabledRanges) {
      enabledRange = enabledRange.shiftRight(delta);
      for (PostFormatProcessor processor : PostFormatProcessor.EP_NAME.getExtensionList()) {
        TextRange processedRange = processor.processText(file, enabledRange, settings);
        delta += processedRange.getLength() - enabledRange.getLength();
      }
    }
  }

  public static class RangeFormatInfo{
    private final SmartPsiElementPointer<?> startPointer;
    private final SmartPsiElementPointer<?> endPointer;
    private final boolean                   fromStart;
    private final boolean                   toEnd;

    RangeFormatInfo(@Nullable SmartPsiElementPointer<?> startPointer,
                    @Nullable SmartPsiElementPointer<?> endPointer,
                    boolean fromStart,
                    boolean toEnd)
    {
      this.startPointer = startPointer;
      this.endPointer = endPointer;
      this.fromStart = fromStart;
      this.toEnd = toEnd;
    }
  }

  @Nullable
  public static PsiElement findElementInTreeWithFormatterEnabled(final PsiFile file, final int offset) {
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

  private static CodeStyleSettings getSettings(@NotNull PsiFile file) {
    return CodeStyle.getSettings(file);
  }


}
