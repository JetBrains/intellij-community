/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;

/**
 * @author Bas Leijdekkers
 */
public class JavaMatchingStrategy implements MatchingStrategy {

  private static final JavaMatchingStrategy INSTANCE = new JavaMatchingStrategy();

  private JavaMatchingStrategy() {}

  @Override
  public boolean continueMatching(final PsiElement start) {
    final Language language = start.getLanguage();
    return language == JavaLanguage.INSTANCE || "JSP".equals(language.getID());
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }

  public static MatchingStrategy getInstance() {
    return INSTANCE;
  }
}
