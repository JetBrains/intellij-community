// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ProblemDescriptorUtil {
  public static final int NONE = 0x00000000;
  static final int APPEND_LINE_NUMBER = 0x00000001;
  public static final int TRIM_AT_TREE_END = 0x00000004;

  @MagicConstant(flags = {NONE, APPEND_LINE_NUMBER, TRIM_AT_TREE_END})
  @interface FlagConstant {
  }

  public static final Couple<String> XML_CODE_MARKER = Couple.of("<xml-code>", "</xml-code>");

  public static String extractHighlightedText(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) return "";
    String ref = psiElement.getText();
    if (descriptor instanceof ProblemDescriptorBase) {
      TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
      final TextRange elementRange = psiElement.getTextRange();
      if (textRange != null && elementRange != null) {
        textRange = textRange.shiftRight(-elementRange.getStartOffset());
        if (textRange.getStartOffset() >= 0 && textRange.getEndOffset() <= elementRange.getLength()) {
          ref = textRange.substring(ref);
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
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, @Nullable PsiElement element, @FlagConstant int flags) {
    String message = descriptor.getDescriptionTemplate();

    // no message. Should not be the case if inspection correctly implemented.
    // noinspection ConstantConditions
    if (message == null) return "";

    if ((flags & APPEND_LINE_NUMBER) != 0 &&
        descriptor instanceof ProblemDescriptor &&
        !message.contains("#ref") &&
        message.contains("#loc")) {
      final int lineNumber = ((ProblemDescriptor)descriptor).getLineNumber();
      if (lineNumber >= 0) {
        message = StringUtil.replace(message, "#loc", "(" + InspectionsBundle.message("inspection.export.results.at.line") + " " + (lineNumber + 1) + ")");
      }
    }
    message = unescapeTags(message);
    message = StringUtil.replace(message, "#loc ", "");
    message = StringUtil.replace(message, " #loc", "");
    message = StringUtil.replace(message, "#loc", "");
    if (message.contains("#ref")) {
      String ref = extractHighlightedText(descriptor, element);
      message = StringUtil.replace(message, "#ref", ref);
    }

    final int endIndex = (flags & TRIM_AT_TREE_END) != 0 ? message.indexOf("#treeend") : -1;
    if (endIndex > 0) {
      message = message.substring(0, endIndex);
    }
    message = StringUtil.replace(message, "#end", "");
    message = StringUtil.replace(message, "#treeend", "");

    return message.trim();
  }

  public static String unescapeTags(String message) {
    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    message = message.contains(XML_CODE_MARKER.first) ? unescapeXmlCode(message) : StringUtil.unescapeXml(message);
    return message;
  }

  private static String unescapeXmlCode(final String message) {
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
      } else {
        builder.append(StringUtil.unescapeXml(string));
      }
    }
    return builder.toString();
  }

  @NotNull
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, PsiElement element) {
    return renderDescriptionMessage(descriptor, element, false);
  }

  @NotNull
  public static HighlightInfoType highlightTypeFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                              @NotNull HighlightSeverity severity,
                                                              @NotNull SeverityRegistrar severityRegistrar) {
    final ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
    final HighlightInfoType highlightInfoType = getHighlightInfoType(highlightType, severity, severityRegistrar);
    if (highlightInfoType == HighlightSeverity.INFORMATION) {
      final TextAttributesKey attributes = ((ProblemDescriptorBase)problemDescriptor).getEnforcedTextAttributes();
      if (attributes != null) {
        return new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, attributes);
      }
    }
    return highlightInfoType;
  }

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
      case ERROR:
        return HighlightInfoType.WRONG_REF;
      case GENERIC_ERROR:
        return HighlightInfoType.ERROR;
      case INFORMATION:
        return HighlightInfoType.INFORMATION;
    }
    throw new RuntimeException("Cannot map " + highlightType);
  }
}
