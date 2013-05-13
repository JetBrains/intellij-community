/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ProblemDescriptorUtil {
  public static String extractHighlightedText(@NotNull CommonProblemDescriptor descriptor, PsiElement psiElement) {
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
    ref = StringUtil.replaceChar(ref, '\n', ' ').trim();
    ref = StringUtil.first(ref, 100, true);
    return ref;
  }

  @NotNull
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, PsiElement element, boolean appendLineNumber) {
    String message = descriptor.getDescriptionTemplate();

    // no message. Should not be the case if inspection correctly implemented.
    // noinspection ConstantConditions
    if (message == null) return "";

    if (appendLineNumber && descriptor instanceof ProblemDescriptor && !message.contains("#ref") && message.contains("#loc")) {
      final int lineNumber = ((ProblemDescriptor)descriptor).getLineNumber();
      if (lineNumber >= 0) {
        message = StringUtil.replace(message, "#loc", "(" + InspectionsBundle.message("inspection.export.results.at.line") + " " + lineNumber + ")");
      }
    }
    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    message = StringUtil.replace(message, "#loc ", "");
    message = StringUtil.replace(message, " #loc", "");
    message = StringUtil.replace(message, "#loc", "");
    if (message.contains("#ref")) {
      String ref = extractHighlightedText(descriptor, element);
      message = StringUtil.replace(message, "#ref", ref);
    }

    final int endIndex = message.indexOf("#end");
    if (endIndex > 0) {
      message = message.substring(0, endIndex);
    }

    message = StringUtil.unescapeXml(message).trim();
    return message;
  }

  @NotNull
  public static String renderDescriptionMessage(@NotNull CommonProblemDescriptor descriptor, PsiElement element) {
    return renderDescriptionMessage(descriptor, element, false);
  }
}
