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
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

/**
 * @author peter
 */
public abstract class AbstractCompletionContributor<Params extends CompletionParameters> {

  /**
   * Fill completion variants.
   *
   * Invoked sequentially for all registered contributors in order of registration.
   *
   * @param parameters
   * @param result
   * @return Whether to continue variants collecting process. If false, remaining non-visited completion contributors are ignored.
   * @see CompletionService#getVariantsFromContributors(com.intellij.openapi.extensions.ExtensionPointName, CompletionParameters, AbstractCompletionContributor , com.intellij.util.Consumer)
   */
  public abstract void fillCompletionVariants(Params parameters, CompletionResultSet result);

  protected static boolean isPatternSuitable(final ElementPattern<? extends PsiElement> pattern, final CompletionParameters parameters,
                                             final ProcessingContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        PsiElement position = parameters.getPosition();
        return position.isValid() && pattern.accepts(position, context);
      }
    }).booleanValue();
  }
}
