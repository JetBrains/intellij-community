/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.safeDelete;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The extension helps to encapsulate a custom logic for "Safe delete" refactoring.
 */
public interface JavaSafeDeleteDelegate {
  LanguageExtension<JavaSafeDeleteDelegate> EP =
    new LanguageExtension<>("com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate");

  /**
   * Method is used to create usage information according to the input <code>reference</code> to the method
   * and <code>parameter</code> that belongs to the <code>method</code>.
   * The result will be filled into the list of the usages.
   * <p> The method should be called under read action.
   *     A caller should be also aware that an implementation may use an index access,
   *     so using the method in EDT may lead to get the exception from {@link SlowOperations#assertSlowOperationsAreAllowed()}
   */
  void createUsageInfoForParameter(@NotNull PsiReference reference,
                                   @NotNull List<UsageInfo> usages,
                                   @NotNull PsiParameter parameter,
                                   @NotNull PsiMethod method);
}
