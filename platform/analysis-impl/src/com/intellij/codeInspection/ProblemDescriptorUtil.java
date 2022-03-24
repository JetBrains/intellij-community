// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Verifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ProblemDescriptorUtil {
  public static final int NONE = 0x00000000;
  static final int APPEND_LINE_NUMBER = 0x00000001;
  public static final int TRIM_AT_TREE_END = 0x00000004;

  @NonNls private static final String LOC_REFERENCE = "#loc";
  @NonNls private static final String REF_REFERENCE = "#ref";

  @MagicConstant(flags = {NONE, APPEND_LINE_NUMBER, TRIM_AT_TREE_END})
  @interface FlagConstant {
  }

  public static final Couple<String> XML_CODE_MARKER = Couple.of("<xml-code>", "</xml-code>");

  @NotNull
  public static String extractHighlightedText(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement psiElement) {
    TextRange range = descriptor instanceof ProblemDescriptorBase ? ((ProblemDescriptorBase)descriptor).getTextRange() : null;
    return extractHighlightedText(range, psiElement);
  }

  @NotNull
  public static String sanitizeIllegalXmlChars(@NotNull String text) {
    if (Verifier.checkCharacterData(text) == null) return text;
    return text.codePoints().map(cp -> Verifier.isXMLCharacter(cp) ? cp : '?')
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  @NotNull
  public static String extractHighlightedText(@Nullable TextRange range, @Nullable PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) return "";
    String ref = psiElement.getText();
    if (range != null) {
      final TextRange elementRange = psiElement.getTextRange();
      if (elementRange != null) {
        range = range.shiftRight(-elementRange.getStartOffset());
        if (range.getStartOffset() >= 0 && ref.length() > range.getLength()) {
          ref = range.substring(ref);
        }
      }
    }
    ref = ref.replace('\n', ' ').trim();
    ref = StringUtil.first(ref, 100, true);
    return ref.trim().replaceAll("\\s+", " ");
  }

  @NotNull
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element, boolean appendLineNumber) {
    return renderDescriptionMessage(descriptor, element, appendLineNumber ? APPEND_LINE_NUMBER : NONE);
  }

  @NotNull
  @InspectionMessage
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element, @FlagConstant int flags) {
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

  @NotNull
  @InspectionMessage
  @NlsContexts.Tooltip
  private static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor,
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
        message = StringUtil.removeHtmlTags(message, true);
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
  @NotNull
  public static String removeLocReference(@NotNull String message) {
    message = StringUtil.replace(message, LOC_REFERENCE + " ", "");
    message = StringUtil.replace(message, " " + LOC_REFERENCE, "");
    message = StringUtil.replace(message, LOC_REFERENCE, "");
    return message;
  }

  @Contract(pure = true)
  @NotNull
  public static String unescapeTags(@NotNull String message) {
    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    message = message.contains(XML_CODE_MARKER.first) ? unescapeXmlCode(message) :
              !XmlStringUtil.isWrappedInHtml(message) ? StringUtil.unescapeXmlEntities(message) : message;
    return message;
  }

  @NotNull
  private static String unescapeXmlCode(@NotNull String message) {
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

  @NotNull
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element) {
    return renderDescriptionMessage(descriptor, element, false);
  }

  @NotNull
  public static HighlightInfoType highlightTypeFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                              @NotNull HighlightSeverity severity,
                                                              @NotNull SeverityRegistrar severityRegistrar) {
    return getHighlightInfoType(problemDescriptor.getHighlightType(), severity, severityRegistrar);
  }

  @NotNull
  public static HighlightInfoType getHighlightInfoType(@NotNull ProblemHighlightType highlightType,
                                                       @NotNull HighlightSeverity severity,
                                                       @NotNull SeverityRegistrar severityRegistrar) {
    switch (highlightType) {
      case GENERIC_ERROR_OR_WARNING:
        return severityRegistrar.getHighlightInfoTypeBySeverity(severity);
      case LIKE_DEPRECATED:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.DEPRECATED.getAttributesKey());
      case LIKE_MARKED_FOR_REMOVAL:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.MARKED_FOR_REMOVAL.getAttributesKey());
      case LIKE_UNKNOWN_SYMBOL:
        if (severity == HighlightSeverity.ERROR) {
          return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.WRONG_REF.getAttributesKey());
        }
        if (severity == HighlightSeverity.WARNING) {
          return new HighlightInfoType.HighlightInfoTypeImpl(severity, CodeInsightColors.WEAK_WARNING_ATTRIBUTES);
        }
        return severityRegistrar.getHighlightInfoTypeBySeverity(severity);
      case LIKE_UNUSED_SYMBOL:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());
      case INFO:
        return HighlightInfoType.INFO;
      case WEAK_WARNING:
        return HighlightInfoType.WEAK_WARNING;
      case WARNING:
        return HighlightInfoType.WARNING;
      case ERROR:
        return HighlightInfoType.WRONG_REF;
      case GENERIC_ERROR:
        return HighlightInfoType.ERROR;
      case INFORMATION:
        return HighlightInfoType.INFORMATION;
      case POSSIBLE_PROBLEM:
        return HighlightInfoType.POSSIBLE_PROBLEM;
    }
    throw new RuntimeException("Cannot map " + highlightType);
  }
  public static ProblemDescriptor @NotNull [] convertToProblemDescriptors(@NotNull final List<? extends Annotation> annotations, @NotNull final PsiFile file) {
    if (annotations.isEmpty()) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    List<ProblemDescriptor> problems = new ArrayList<>(annotations.size());
    IdentityHashMap<IntentionAction, LocalQuickFix> quickFixMappingCache = new IdentityHashMap<>();
    for (Annotation annotation : annotations) {
      HighlightSeverity severity = annotation.getSeverity();
      int startOffset = annotation.getStartOffset();
      int endOffset = annotation.getEndOffset();

      String message = annotation.getMessage();
      boolean isAfterEndOfLine = annotation.isAfterEndOfLine();
      LocalQuickFix[] quickFixes = toLocalQuickFixes(annotation.getQuickFixes(), quickFixMappingCache);

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
                                                       LocalQuickFix @NotNull [] quickFixes) {
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

  @Nullable
  private static TextRange getRangeInElement(@NotNull PsiElement startElement, int startOffset, PsiElement endElement, int endOffset) {
    if (startElement != endElement) {
      return null;
    }
    TextRange elementTextRange = startElement.getTextRange();
    if (elementTextRange.getStartOffset() == startOffset && elementTextRange.getEndOffset() == endOffset) {
      return null;
    }
    return new TextRange(startOffset - elementTextRange.getStartOffset(), endOffset - elementTextRange.getStartOffset());
  }

  private static LocalQuickFix @NotNull [] toLocalQuickFixes(@Nullable List<? extends Annotation.QuickFixInfo> fixInfos,
                                                             @NotNull Map<IntentionAction, LocalQuickFix> quickFixMappingCache) {
    if (fixInfos == null || fixInfos.isEmpty()) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    LocalQuickFix[] result = new LocalQuickFix[fixInfos.size()];
    int i = 0;
    for (Annotation.QuickFixInfo fixInfo : fixInfos) {
      IntentionAction intentionAction = fixInfo.quickFix;
      final LocalQuickFix fix;
      if (intentionAction instanceof LocalQuickFix) {
        fix = (LocalQuickFix)intentionAction;
      }
      else {
        LocalQuickFix lqf = quickFixMappingCache.get(intentionAction);
        if (lqf == null) {
          lqf = new ExternalAnnotatorInspectionVisitor.LocalQuickFixBackedByIntentionAction(intentionAction);
          quickFixMappingCache.put(intentionAction, lqf);
        }
        fix = lqf;
      }
      result[i++] = fix;
    }
    return result;
  }

  public static ProblemDescriptor toProblemDescriptor(@NotNull PsiFile file, @NotNull HighlightInfo info) {
    List<LocalQuickFix> quickFixes =
      ContainerUtil.mapNotNull(ObjectUtils.notNull(info.quickFixActionRanges, Collections.emptyList()), p -> {
        IntentionAction intention = p.first.getAction();
        if (intention instanceof LocalQuickFix) return (LocalQuickFix)intention;
        if (intention instanceof LocalQuickFixAsIntentionAdapter) {
          return ((LocalQuickFixAsIntentionAdapter)intention).getFix();
        }
        return null;
      });
    return convertToDescriptor(file, info.getSeverity(), info.getStartOffset(), info.getEndOffset(), info.getDescription(), info.isAfterEndOfLine(), quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }
}
