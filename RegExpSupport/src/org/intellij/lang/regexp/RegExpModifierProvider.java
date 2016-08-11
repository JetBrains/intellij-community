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
package org.intellij.lang.regexp;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.MagicConstant;

import java.util.regex.Pattern;

/**
 * Enables support of checking regexp with modifiers, e.g. case insensitivity, new line skip and other options
 * Certain modifier usage depends on language reference
 * @author Anna Bulenkova
 */
public interface RegExpModifierProvider {
  LanguageExtension<RegExpModifierProvider> EP = new LanguageExtension<>("com.intellij.regExpModifierProvider");

  @MagicConstant(flagsFromClass = Pattern.class)
  int getFlags(PsiElement elementInHost, PsiFile regexp);
}
