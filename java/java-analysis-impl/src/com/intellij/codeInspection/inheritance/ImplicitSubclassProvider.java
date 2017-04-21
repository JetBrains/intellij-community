/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.inheritance;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

/**
 * Provides information about classes/interfaces that will be implicitly subclassed/implemented at runtime,
 * e.g. by some framework (CGLIB Proxy in Spring).
 *
 * @author Nicolay Mitropolsky
 * @since 2017.2
 */
public abstract class ImplicitSubclassProvider {
  public static final ExtensionPointName<ImplicitSubclassProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.codeInsight.implicitSubclassProvider");

  /**
   * Checks if this provider could probably provide a subclass for passed psiClass.
   * <b>Note:<b/> this check is expected to be cheap. If it requires long computations then it is better just to return true.
   *
   * @param psiClass a class to check for possible subclass.
   * @return {@code false} if definitely no subclass will be created for the psiClass and all further checks could be skipped;
   * {@code true} if a subclass for the psiClass will probably be created,
   * and then you should check {@link #findOverridingReason(PsiMethod)} and {@link #findOverridingReason(PsiMethod)}
   * methods to find out if there are concrete reasons for the class to be subclassed.
   */
  public abstract boolean isApplicableTo(@NotNull PsiClass psiClass);

  /**
   * Checks if this provider will implicitly subclass passed class.
   * <b>Note:<b/> this method could be computationally costly because in some cases it could require deep annotations checks
   * not only for class but also for all its methods.
   *
   * Default implementation doesn't check methods, so implementors should override this methods if target framework
   * makes decision about overriding basing on methods annotations.
   *
   * @param psiClass a class to check for possible subclass.
   * @return true if class will be subclassed, false - otherwise.
   */
  public boolean providesSubclassFor(@NotNull PsiClass psiClass) {
    return isApplicableTo(psiClass) && findSubclassingReason(psiClass) != null;
  }

  /**
   * <b>Note:</b> assumes that you have called {@link #isApplicableTo(PsiClass)} and will not check it again.
   * So you can get wrong results if you haven't check.
   *
   * @param psiClass a class to check for possible subclass.
   * @return explanation why this class will be implicitly subclassed, or {@code null} if it will not be subclassed.
   */
  @Nls(capitalization = Sentence)
  @Nullable
  public abstract String findSubclassingReason(@NotNull PsiClass psiClass);

  /**
   * <b>Note:</b> assumes that you have called {@link #isApplicableTo(PsiClass)} and will not check it again.
   * So you can get wrong results if you haven't check.
   *
   * @param psiMethod a method to check for implicit override.
   * @return explanation why this method will be implicitly overridden, or {@code null} if it will not be overridden.
   */
  @Nls(capitalization = Sentence)
  @Nullable
  public String findOverridingReason(@NotNull PsiMethod psiMethod) {
    return null;
  }
}
