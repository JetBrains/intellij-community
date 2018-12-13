/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.testIntegration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Triggered when the user uses the "Navigation / Test"  action (= jump to test shortcut). 
 * Corresponding extension point qualified name is {@code com.intellij.testFinder}.
 */
public interface TestFinder {
  ExtensionPointName<TestFinder> EP_NAME = ExtensionPointName.create("com.intellij.testFinder");

  /**
   * Retrieve the source element (PsiFile) to handle some UI elements, like the name displayed in "Choose Test for {file name}".
   *
   * @param from PsiElement where the cursor was when "Navigate to test" was triggered
   * @return the parent PsiFile of the PsiElement where the cursor was when the test finder was invoked
   */
  @Nullable
  PsiElement findSourceElement(@NotNull PsiElement from);

  /**
   * Finds tests for given class.
   *
   * @param element may by of any language but not specific to a current test finder domain language
   * @return found tests for class
   */
  @NotNull
  Collection<PsiElement> findTestsForClass(@NotNull PsiElement element);

  /**
   * Finds classes for given test.
   *
   * @param element may by of any language but not specific to a current test finder domain language
   * @return found classes for test
   */
  @NotNull
  Collection<PsiElement> findClassesForTest(@NotNull PsiElement element);

  boolean isTest(@NotNull PsiElement element);
}
