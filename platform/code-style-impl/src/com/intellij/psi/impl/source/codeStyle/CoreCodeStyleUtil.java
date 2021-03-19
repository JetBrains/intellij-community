// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.diagnostic.PluginException;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.formatting.FormatterTagHandler;
import com.intellij.lang.ASTNode;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public class CoreCodeStyleUtil {
  private final static Logger LOG = Logger.getInstance(CoreCodeStyleUtil.class);

  @NonNls private static final String DUMMY_IDENTIFIER = "xxx";

  private static final ThreadLocal<ProcessingUnderProgressInfo> SEQUENTIAL_PROCESSING_ALLOWED
    = ThreadLocal.withInitial(() -> new ProcessingUnderProgressInfo());

  private CoreCodeStyleUtil() {
  }

  static PsiElement postProcessElement(@NotNull PsiFile file, @NotNull final PsiElement formatted) {
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

  public static void formatRanges(@NotNull PsiFile file, @NotNull FormatTextRanges ranges) {
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

    if (!ExternalFormatProcessor.useExternalFormatter(file)) {
      final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(getSettings(file), file.getLanguage());
      codeFormatter.processText(file, ranges, true);
    }

    for (RangeFormatInfo info : infos) {
      final PsiElement startElement = info.startPointer == null ? null : info.startPointer.getElement();
      final PsiElement endElement = info.endPointer == null ? null : info.endPointer.getElement();
      if ((startElement != null || info.fromStart) && (endElement != null || info.toEnd)) {
        TextRange currRange = new TextRange(info.fromStart ? 0 : startElement.getTextRange().getStartOffset(),
                                            info.toEnd ? file.getTextLength() : endElement.getTextRange().getEndOffset());
        if (ExternalFormatProcessor.useExternalFormatter(file)) {
          ExternalFormatProcessor.formatRangeInFile(file, currRange, false, false);
        }
        else {
          postProcessText(file, currRange);
        }
      }
      if (info.startPointer != null) smartPointerManager.removePointer(info.startPointer);
      if (info.endPointer != null) smartPointerManager.removePointer(info.endPointer);
    }
  }

  private static void postProcessText(@NotNull final PsiFile file, @NotNull final TextRange textRange) {
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

  private static class RangeFormatInfo{
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

  /**
   * Formatter trims line that contains white spaces symbols only, however, there is a possible case that we want
   * to preserve them for particular line
   * (e.g. for live template that defines line with whitespaces that contains $END$ marker: templateText   $END$).
   * <p/>
   * Current approach is to do the following:
   * <pre>
   * <ol>
   *   <li>Insert dummy text at the end of the blank line which white space symbols should be preserved;</li>
   *   <li>Perform formatting;</li>
   *   <li>Remove dummy text;</li>
   * </ol>
   * </pre>
   * <p/>
   * This method inserts that dummy comment (fallback to identifier {@code xxx}, see {@link #createMarker(PsiFile, int)})
   * if necessary.
   * <p/>
   * <b>Note:</b> it's expected that the whole white space region that contains given offset is processed in a way that all
   * {@link RangeMarker range markers} registered for the given offset are expanded to the whole white space region.
   * E.g. there is a possible case that particular range marker serves for defining formatting range, hence, its start/end offsets
   * are updated correspondingly after current method call and whole white space region is reformatted.
   *
   * @param file        target PSI file
   * @param document    target document
   * @param offset      offset that defines end boundary of the target line text fragment (start boundary is the first line's symbol)
   * @return            text range that points to the newly inserted dummy text if any; {@code null} otherwise
   * @throws IncorrectOperationException  if given file is read-only
   */
  @Nullable
  public static TextRange insertNewLineIndentMarker(@NotNull PsiFile file, @NotNull Document document, int offset) {
    CharSequence text = document.getImmutableCharSequence();
    if (offset <= 0 || offset >= text.length() || !isWhiteSpaceSymbol(text.charAt(offset))) {
      return null;
    }

    if (!isWhiteSpaceSymbol(text.charAt(offset - 1))) {
      return null; // no whitespaces before offset
    }

    int end = offset;
    for (; end < text.length(); end++) {
      if (text.charAt(end) == '\n') {
        break; // line is empty till the end
      }
      if (!isWhiteSpaceSymbol(text.charAt(end))) {
        return null;
      }
    }

    String marker = createMarker(file, offset);
    document.insertString(offset, marker);
    return new TextRange(offset, offset + marker.length());
  }

  private static boolean isWhiteSpaceSymbol(char c) {
    return c == ' ' || c == '\t' || c == '\n';
  }

  private static @NotNull String createMarker(@NotNull PsiFile file, int offset) {
    Project project = file.getProject();
    PsiElement injectedElement = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset);
    Language language = injectedElement != null ? injectedElement.getLanguage() : PsiUtilCore.getLanguageAtOffset(file, offset);

    setSequentialProcessingAllowed(false);
    NewLineIndentMarkerProvider markerProvider = NewLineIndentMarkerProvider.EP.forLanguage(language);
    String marker = markerProvider == null ? null : markerProvider.createMarker(file, offset);
    if (marker != null) {
      return marker;
    }

    PsiComment comment = null;
    try {
      comment = PsiParserFacade.SERVICE.getInstance(project).createLineOrBlockCommentFromText(language, "");
    }
    catch (Throwable ignored) {
    }
    String text = comment != null ? comment.getText() : null;
    return text != null ? text : DUMMY_IDENTIFIER;
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

  /**
   * Allows to check if given offset points to white space element within the given PSI file and return that white space
   * element in the case of positive answer.
   *
   * @param file    target file
   * @param offset  offset that might point to white space element within the given PSI file
   * @return        target white space element for the given offset within the given file (if any); {@code null} otherwise
   */
  @Nullable
  public static PsiElement findWhiteSpaceNode(@NotNull PsiFile file, int offset) {
    return doFindWhiteSpaceNode(file, offset).first;
  }

  @NotNull
  private static Pair<PsiElement, CharTable> doFindWhiteSpaceNode(@NotNull PsiFile file, int offset) {
    ASTNode astNode = SourceTreeToPsiMap.psiElementToTree(file);
    if (!(astNode instanceof FileElement)) {
      return new Pair<>(null, null);
    }
    PsiElement elementAt = InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, offset);
    final CharTable charTable = ((FileElement)astNode).getCharTable();
    if (elementAt == null) {
      elementAt = findElementInTreeWithFormatterEnabled(file, offset);
    }

    if( elementAt == null) {
      return new Pair<>(null, charTable);
    }
    ASTNode node = elementAt.getNode();
    if (node == null || node.getElementType() != TokenType.WHITE_SPACE) {
      return new Pair<>(null, charTable);
    }
    return Pair.create(elementAt, charTable);
  }
}
