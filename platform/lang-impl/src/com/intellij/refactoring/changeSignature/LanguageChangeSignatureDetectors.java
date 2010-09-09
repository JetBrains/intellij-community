/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: anna
* Date: Sep 9, 2010
*/
class LanguageChangeSignatureDetectors extends LanguageExtension<LanguageChangeSignatureDetector> {
  public static final LanguageChangeSignatureDetectors INSTANCE = new LanguageChangeSignatureDetectors();

  LanguageChangeSignatureDetectors() {
    super("com.intellij.changeSignatureDetector");
  }

  @Nullable
  protected static ChangeInfo createCurrentChangeInfo(@NotNull PsiElement element, @Nullable ChangeInfo changeInfo) {
    final LanguageChangeSignatureDetector detector = INSTANCE.forLanguage(element.getLanguage());
    return detector != null ? detector.createCurrentChangeSignature(element, changeInfo) : null;
  }

  public static boolean isSuitableForLanguage(Language lang) {
    return INSTANCE.forLanguage(lang) != null;
  }
}
