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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiJShellSyntheticElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class JShellCompletionContributor extends CompletionContributor implements DumbAware {
  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet resultSet) {
    resultSet.runRemainingContributors(parameters, r -> {
      if (!(r.getLookupElement().getPsiElement() instanceof PsiJShellSyntheticElement)) {
        resultSet.passResult(r);
      }
    });
  }

}
