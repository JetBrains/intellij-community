// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.Annotation.QuickFixInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Verifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ProblemDescriptorUtil {
  public static final int NONE = 0x00000000;
  static final int APPEND_LINE_NUMBER = 0x00000001;
  public static final int TRIM_AT_TREE_END = 0x00000004;

  public static final @NonNls String LOC_REFERENCE = "#loc";
  public static final @NonNls String REF_REFERENCE = "#ref";

  @MagicConstant(flags = {NONE, APPEND_LINE_NUMBER, TRIM_AT_TREE_END})
  @interface FlagConstant {
  }

  public static final Couple<String> XML_CODE_MARKER = Couple.of("<xml-code>", "</xml-code>");

  public static @NotNull String extractHighlightedText(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement psiElement) {
    TextRange range = descriptor instanceof ProblemDescriptorBase ? ((ProblemDescriptorBase)descriptor).getTextRange() : null;
    return extractHighlightedText(range, psiElement);
  }

  public static @NotNull String sanitizeIllegalXmlChars(@NotNull String text) {
    if (Verifier.checkCharacterData(text) == null) return text;
    return text.codePoints().map(cp -> Verifier.isXMLCharacter(cp) ? cp : '?')
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  public static @NotNull String extractHighlightedText(@Nullable TextRange range, @Nullable PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) return "";
    CharSequence fileText = psiElement.getContainingFile().getViewProvider().getDocument().getImmutableCharSequence();
    TextRange elementRange = psiElement.getTextRange();
    CharSequence elementSequence;
    if (elementRange == null) {
      elementSequence = psiElement.getText();
    }
    else {
      elementSequence = fileText.subSequence(elementRange.getStartOffset(), elementRange.getEndOffset());
    }
    CharSequence ref = elementSequence;
    if (range != null) {
      if (elementRange != null) {
        range = range.shiftRight(-elementRange.getStartOffset());
        if (range.getStartOffset() >= 0 && elementSequence.length() > range.getLength()) {
          ref = elementSequence.subSequence(range.getStartOffset(), range.getEndOffset());
        }
      }
    }
    String result = StringUtil.first(ref, 100, true).toString();
    return StringUtil.collapseWhiteSpace(result);
  }

  public static @NotNull String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element, boolean appendLineNumber) {
    return renderDescriptionMessage(descriptor, element, appendLineNumber ? APPEND_LINE_NUMBER : NONE);
  }

  public static @NotNull @InspectionMessage String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element, @FlagConstant int flags) {
    String message = descriptor.getDescriptionTemplate();

    // no message. This should not be the case if the inspection is correctly implemented.
    // noinspection ConstantConditions
    if (message == null) return "";

    return renderDescriptionMessage(descriptor, element, flags, message);
  }

  public static ProblemPresentation renderDescriptor(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element, @FlagConstant int flags) {
    NotNullLazyValue<@InspectionMessage String> descTemplate = NotNullLazyValue.volatileLazy(
      () -> StringUtil.notNullize(descriptor.getDescriptionTemplate()));
    NotNullLazyValue<@InspectionMessage String> tooltipTemplate = NotNullLazyValue.volatileLazy(
      () -> descriptor instanceof ProblemDescriptor
            ? StringUtil.notNullize(((ProblemDescriptor)descriptor).getTooltipTemplate())
            : descTemplate.getValue()
    );
    NotNullLazyValue<@InspectionMessage String> description = NotNullLazyValue.volatileLazy(
      () -> renderDescriptionMessage(descriptor, element, flags, descTemplate.getValue()));
    NotNullLazyValue<@NlsContexts.Tooltip String> tooltip = NotNullLazyValue.volatileLazy(() -> {
      String template = tooltipTemplate.getValue();
      return template.equals(descTemplate.getValue()) ? description.getValue()
                                                      : renderDescriptionMessage(descriptor, element, flags, template);
    });
    return new ProblemPresentation() {
      @Override
      public @NotNull String getDescription() {
        return description.getValue();
      }

      @Override
      public @NotNull String getTooltip() {
        return tooltip.getValue();
      }
    };
  }

  public interface ProblemPresentation {
    @NotNull @InspectionMessage String getDescription();
    @NotNull @NlsContexts.Tooltip String getTooltip();
  }

  private static @NotNull @InspectionMessage @NlsContexts.Tooltip String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor,
                                                                                                  @Nullable PsiElement element,
                                                                                                  @FlagConstant int flags,
                                                                                                  @InspectionMessage String template) {
    String message = template;
    if ((flags & APPEND_LINE_NUMBER) != 0 &&
        descriptor instanceof ProblemDescriptor &&
        !message.contains(REF_REFERENCE) &&
        message.contains(LOC_REFERENCE)) {
      final int lineNumber = ((ProblemDescriptor)descriptor).getLineNumber();
      if (lineNumber >= 0) {
        message = StringUtil.replace(message, LOC_REFERENCE, "(" + AnalysisBundle.message("inspection.export.results.at.line") + " " + (lineNumber + 1) + ")");
      }
    }
    message = unescapeTags(message);
    message = removeLocReference(message);

    if ((flags & TRIM_AT_TREE_END) != 0) {
      if (XmlStringUtil.isWrappedInHtml(message)) {
        message = StringUtil.removeHtmlTags(message, true).replace('\n', ' ');
      }

      final int endIndex = message.indexOf("#treeend");
      if (endIndex > 0) {
        message = message.substring(0, endIndex);
      }
    }

    if (message.contains(REF_REFERENCE)) {
      String ref = extractHighlightedText(descriptor, element);
      message = StringUtil.replace(message, REF_REFERENCE, ref);
    }

    message = StringUtil.replace(message, "#end", "");
    message = StringUtil.replace(message, "#treeend", "");

    return message.trim();
  }

  @Contract(pure = true)
  public static @NotNull String removeLocReference(@NotNull String message) {
    message = StringUtil.replace(message, LOC_REFERENCE + " ", "");
    message = StringUtil.replace(message, " " + LOC_REFERENCE, "");
    message = StringUtil.replace(message, LOC_REFERENCE, "");
    return message;
  }

  @Contract(pure = true)
  public static @NotNull String unescapeTags(@NotNull String message) {
    if (!XmlStringUtil.isWrappedInHtml(message)) {
      message = StringUtil.replace(message, "<code>", "'");
      message = StringUtil.replace(message, "</code>", "'");
    }
    message = message.contains(XML_CODE_MARKER.first) ? unescapeXmlCode(message) :
              !XmlStringUtil.isWrappedInHtml(message) ? StringUtil.unescapeXmlEntities(message) : message;
    return message;
  }

  private static @NotNull String unescapeXmlCode(@NotNull String message) {
    List<String> strings = new ArrayList<>();
    for (String string : StringUtil.split(message, XML_CODE_MARKER.first)) {
      if (string.contains(XML_CODE_MARKER.second)) {
        strings.addAll(StringUtil.split(string, XML_CODE_MARKER.second, false));
      }
      else {
        strings.add(string);
      }
    }
    StringBuilder builder = new StringBuilder();
    for (String string : strings) {
      if (string.contains(XML_CODE_MARKER.second)) {
        builder.append(string.replace(XML_CODE_MARKER.second, ""));
      }
      else {
        builder.append(StringUtil.unescapeXmlEntities(string));
      }
    }
    return builder.toString();
  }

  public static @NotNull String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element) {
    return renderDescriptionMessage(descriptor, element, false);
  }

  public static @NotNull HighlightInfoType highlightTypeFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                                       @NotNull HighlightSeverity severity,
                                                                       @NotNull SeverityRegistrar severityRegistrar) {
    return getHighlightInfoType(problemDescriptor.getHighlightType(), severity, severityRegistrar);
  }

  public static @NotNull HighlightInfoType getHighlightInfoType(@NotNull ProblemHighlightType highlightType,
                                                                @NotNull HighlightSeverity severity,
                                                                @NotNull SeverityRegistrar severityRegistrar) {
    return switch (highlightType) {
      case GENERIC_ERROR_OR_WARNING -> severityRegistrar.getHighlightInfoTypeBySeverity(severity);
      case LIKE_DEPRECATED -> new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.DEPRECATED.getAttributesKey());
      case LIKE_MARKED_FOR_REMOVAL ->
        new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.MARKED_FOR_REMOVAL.getAttributesKey());
      case LIKE_UNKNOWN_SYMBOL -> {
        if (severity == HighlightSeverity.ERROR) {
          yield new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.WRONG_REF.getAttributesKey());
        }
        if (severity == HighlightSeverity.WARNING) {
          yield new HighlightInfoType.HighlightInfoTypeImpl(severity, CodeInsightColors.WEAK_WARNING_ATTRIBUTES);
        }
        yield severityRegistrar.getHighlightInfoTypeBySeverity(severity);
      }
      case LIKE_UNUSED_SYMBOL -> new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());
      case INFO -> HighlightInfoType.INFO;
      case WEAK_WARNING -> HighlightInfoType.WEAK_WARNING;
      case WARNING -> HighlightInfoType.WARNING;
      case ERROR -> HighlightInfoType.WRONG_REF;
      case GENERIC_ERROR -> HighlightInfoType.ERROR;
      case INFORMATION -> HighlightInfoType.INFORMATION;
      case POSSIBLE_PROBLEM -> HighlightInfoType.POSSIBLE_PROBLEM;
    };
  }
  public static ProblemDescriptor @NotNull [] convertToProblemDescriptors(final @NotNull List<? extends Annotation> annotations, final @NotNull PsiFile file) {
    if (annotations.isEmpty()) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    List<ProblemDescriptor> problems = new ArrayList<>(annotations.size());
    for (Annotation annotation : annotations) {
      HighlightSeverity severity = annotation.getSeverity();
      int startOffset = annotation.getStartOffset();
      int endOffset = annotation.getEndOffset();

      String message = StringUtil.notNullize(annotation.getMessage());
      boolean isAfterEndOfLine = annotation.isAfterEndOfLine();
      LocalQuickFix[] quickFixes = toLocalQuickFixes(annotation.getQuickFixes());

      ProblemDescriptor descriptor = convertToDescriptor(file, severity, startOffset, endOffset, message, isAfterEndOfLine, quickFixes);
      if (descriptor != null) {
        problems.add(descriptor);
      }
    }
    return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static ProblemDescriptor convertToDescriptor(@NotNull PsiFile file,
                                                       @NotNull HighlightSeverity severity,
                                                       int startOffset,
                                                       int endOffset,
                                                       @NotNull @InspectionMessage String message,
                                                       boolean isAfterEndOfLine,
                                                       @NotNull LocalQuickFix @NotNull [] quickFixes) {
    if (severity == HighlightSeverity.INFORMATION ||
        startOffset == endOffset && !isAfterEndOfLine) {
      return null;
    }

    final PsiElement startElement;
    final PsiElement endElement;
    if (startOffset == endOffset) {
      startElement = endElement = file.findElementAt(endOffset - 1);
    }
    else {
      startElement = file.findElementAt(startOffset);
      endElement = file.findElementAt(endOffset - 1);
    }
    if (startElement == null || endElement == null) {
      return null;
    }
    final TextRange rangeInElement = getRangeInElement(startElement, startOffset, endElement, endOffset);

    ProblemHighlightType highlightType = HighlightInfo.convertSeverityToProblemHighlight(severity);
    return new ProblemDescriptorBase(startElement,
                                     endElement,
                                     message,
                                     quickFixes,
                                     highlightType,
                                     isAfterEndOfLine,
                                     rangeInElement,
                                     true,
                                     false);
  }

  private static @Nullable TextRange getRangeInElement(@NotNull PsiElement startElement, int startOffset, PsiElement endElement, int endOffset) {
    if (startElement != endElement) {
      return null;
    }
    TextRange elementTextRange = startElement.getTextRange();
    if (elementTextRange.getStartOffset() == startOffset && elementTextRange.getEndOffset() == endOffset) {
      return null;
    }
    return new TextRange(startOffset - elementTextRange.getStartOffset(), endOffset - elementTextRange.getStartOffset());
  }

  private static @NotNull LocalQuickFix @NotNull [] toLocalQuickFixes(@Nullable List<QuickFixInfo> fixInfos) {
    if (fixInfos == null || fixInfos.isEmpty()) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    return ContainerUtil.map2Array(fixInfos, LocalQuickFix.class, QuickFixInfo::getLocalQuickFix);
  }

  public static ProblemDescriptor toProblemDescriptor(@NotNull PsiFile file, @NotNull HighlightInfo info) {
    List<LocalQuickFix> quickFixes = new ArrayList<>();
    info.findRegisteredQuickFix((descriptor, range) -> {
      IntentionAction intention = descriptor.getAction();
      LocalQuickFix fix = intention instanceof LocalQuickFix localFix ? localFix : QuickFixWrapper.unwrap(intention);
      if (fix != null) {
        quickFixes.add(fix);
      }
      return null;
    });
    return convertToDescriptor(file, info.getSeverity(), info.getStartOffset(), info.getEndOffset(), info.getDescription(), info.isAfterEndOfLine(), quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }
}
