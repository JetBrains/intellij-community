// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.impl.RegExpElementImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpReplacementUtil {

  private RegExpReplacementUtil() {}

  public static void replaceInContext(@NotNull PsiElement element, @NotNull String text) {
    final PsiFile file = element.getContainingFile();
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
    if (injectedLanguageManager.isInjectedFragment(file)) {
      final PsiElement context = file.getContext();
      ElementManipulator<PsiElement> manipulator = context == null ? null : ElementManipulators.getManipulator(context);
      if (manipulator != null) {
        // use element manipulator to process escape sequences correctly for all supported languages
        final TextRange range = manipulator.getRangeInElement(context);
        if (manipulator.handleContentChange(context, range.cutOut(element.getTextRange()), text) != null) {
          return;
        }
      }
      if (RegExpElementImpl.isLiteralExpression(context)) {
        text = StringUtil.escapeStringCharacters(text);
      }
      else if (context instanceof XmlElement) {
        text = XmlStringUtil.escapeString(text);
      }
    }
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
    assert document != null;
    final TextRange replaceRange = element.getTextRange();
    document.replaceString(replaceRange.getStartOffset(), replaceRange.getEndOffset(), text);
  }
}
