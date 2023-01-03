// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * Use an RegExpMatcherProvider implementation to replace the regexp matcher used for the Check RegExp intention.
 * It should retrieve its own modifiers (case insensitive matching, dotall mode etc),
 * a RegExpModifierProvider will not be used.
 * @author Bas Leijdekkers
 */
public interface RegExpMatcherProvider {

  LanguageExtension<RegExpMatcherProvider> EP = new LanguageExtension<>("com.intellij.regExpMatcherProvider");

  /**
   *
   * @param regExp  the regexp to use for matching
   * @param regExpFile  the psi tree of the regexp. Can be used for preprocessing for example.
   * @param elementInHost  the host language element the regexp is injected in
   * @param sampleText  the text to match on
   * @param timeoutMillis  stop the matching after this time, if the regexp engine is interruptible in some way
   *                       (see e.g. {@link StringUtil#newBombedCharSequence(java.lang.CharSequence, long)}
   * @return the result of the match
   */
  @Nullable
  RegExpMatchResult matches(String regExp,
                            PsiFile regExpFile,
                            PsiElement elementInHost,
                            String sampleText,
                            long timeoutMillis);
}
