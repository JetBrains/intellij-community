// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.regexp.psi.impl.RegExpElementImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class RegExpReplacementUtil {

  /**
   * Dummy text that never needs escaping
   */
  private static final String DUMMY = "a";

  private RegExpReplacementUtil() {}

  public static void replaceInContext(@NotNull PsiElement element, @NotNull String text) {
    replaceInContext(element, text, null);
  }

  public static void replaceInContext(@NotNull PsiElement element, @NotNull String text, TextRange range) {
    final PsiFile file = element.getContainingFile();
    text = escapeForContext(text, file);
    final Document document = file.getViewProvider().getDocument();
    assert document != null;
    final TextRange replaceRange = element.getTextRange();
    final int startOffset = replaceRange.getStartOffset();
    if (range != null) {
      document.replaceString(startOffset + range.getStartOffset(), startOffset + range.getEndOffset(), text);
    }
    else {
      document.replaceString(startOffset, replaceRange.getEndOffset(), text);
    }
  }

  private static String escapeForContext(String text, PsiFile file) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
    if (injectedLanguageManager.isInjectedFragment(file)) {
      final PsiElement context = file.getContext();
      final ElementManipulator<PsiElement> manipulator = context == null ? null : ElementManipulators.getManipulator(context);
      if (manipulator != null) {
        // use element manipulator to process escape sequences correctly for all supported languages
        final PsiElement copy = context.copy(); // create a copy to avoid original element modifications
        final PsiElement newElement = manipulator.handleContentChange(copy, DUMMY + text);
        if (newElement != null) {
          final String newElementText = newElement.getText();
          final TextRange newRange = manipulator.getRangeInElement(newElement);
          return newElementText.substring(newRange.getStartOffset() + DUMMY.length(), newRange.getEndOffset());
        }
      }
      if (RegExpElementImpl.isLiteralExpression(context)) {
        // otherwise, just pretend it is a Java-style string
        return StringUtil.escapeStringCharacters(text);
      }
      else if (context instanceof XmlElement) {
        return XmlStringUtil.escapeString(text);
      }
    }
    return text;
  }
}
