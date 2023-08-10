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
package com.intellij.codeInsight.runner;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension that can provide a custom entry point for Java application (like a {@code main()} method).
 * For example, a custom framework (like JavaFX) may have a separate entry point.
 * These extensions used in some methods in {@link PsiMethodUtil}, like {@link PsiMethodUtil#hasMainMethod(PsiClass)},
 * and can affect the behavior of various IDE features.
 * <p>
 *   The methods of this extension might be called from {@linkplain com.intellij.openapi.project.DumbService dumb mode}.
 * </p>
 */
public interface JavaMainMethodProvider {

  ExtensionPointName<JavaMainMethodProvider> EP_NAME = ExtensionPointName.create("com.intellij.javaMainMethodProvider");

  /**
   * @param clazz class to check
   * @return true if this provider is applicable for a given class.
   */
  @Contract(pure = true)
  boolean isApplicable(@NotNull PsiClass clazz);

  /**
   * @param clazz class to check
   * @return true if the supplied class has the entry point method. The results are unspecified if
   * {@link #isApplicable(PsiClass)} method returns false for the same class.
   */
  @Contract(pure = true)
  boolean hasMainMethod(@NotNull PsiClass clazz);

  /**
   * @param clazz class to check
   * @return a method of given class that is the entry point method, null if there's no entry point method
   * in a given class. The results are unspecified if {@link #isApplicable(PsiClass)}
   * method returns false for the same class.
   */
  @Contract(pure = true)
  @Nullable PsiMethod findMainInClass(@NotNull PsiClass clazz);
}
