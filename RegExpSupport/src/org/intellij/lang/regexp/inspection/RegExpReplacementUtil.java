// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.regexp.psi.impl.RegExpElementImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpReplacementUtil {

  private RegExpReplacementUtil() {}

  public static void replaceInContext(@NotNull PsiElement element, @NotNull String text) {
    final PsiFile file = element.getContainingFile();
    text = escapeForContext(text, file);
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
    assert document != null;
    final TextRange replaceRange = element.getTextRange();
    document.replaceString(replaceRange.getStartOffset(), replaceRange.getEndOffset(), text);
  }

  private static String escapeForContext(String text, PsiFile file) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
    if (injectedLanguageManager.isInjectedFragment(file)) {
      final PsiElement context = file.getContext();
      ElementManipulator<PsiElement> manipulator = context == null ? null : ElementManipulators.getManipulator(context);
      if (manipulator != null) {
        // use element manipulator to process escape sequences correctly for all supported languages
        PsiElement copy = context.copy(); // create a copy to avoid original element modifications
        PsiElement newElement = manipulator.handleContentChange(copy, text);
        if (newElement != null) {
          String newElementText = newElement.getText();
          TextRange newRange = manipulator.getRangeInElement(newElement);
          return newElementText.substring(newRange.getStartOffset(), newRange.getEndOffset());
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
