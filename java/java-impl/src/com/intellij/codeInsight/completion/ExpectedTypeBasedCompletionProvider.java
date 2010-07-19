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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author peter
*/
public abstract class ExpectedTypeBasedCompletionProvider extends CompletionProvider<CompletionParameters> {

  public ExpectedTypeBasedCompletionProvider() {
    super(false);
  }

  public void addCompletions(@NotNull final CompletionParameters params, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
    final PsiElement position = params.getPosition();
    if (position.getParent() instanceof PsiLiteralExpression) return;

    final THashSet<ExpectedTypeInfo> infos = new THashSet<ExpectedTypeInfo>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ContainerUtil.addAll(infos, JavaSmartCompletionContributor.getExpectedTypes(params));
      }
    });
    addCompletions(params, result, infos);
  }

  protected abstract void addCompletions(CompletionParameters params, CompletionResultSet result, Collection<ExpectedTypeInfo> infos);
}
