/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

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
  @NotNull
  RegExpMatchResult matches(String regExp,
                            PsiFile regExpFile,
                            PsiElement elementInHost,
                            String sampleText,
                            long timeoutMillis);
}
