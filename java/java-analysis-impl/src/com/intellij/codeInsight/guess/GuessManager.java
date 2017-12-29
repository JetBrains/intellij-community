
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
package com.intellij.codeInsight.guess;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class GuessManager {
  public static GuessManager getInstance(Project project) {
    return ServiceManager.getService(project, GuessManager.class);
  }

  @NotNull
  public abstract PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore);

  @NotNull
  public abstract PsiType[] guessTypeToCast(PsiExpression expr);

  @NotNull 
  public abstract MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@NotNull PsiExpression forPlace);

  @NotNull
  public abstract List<PsiType> getControlFlowExpressionTypeConjuncts(@NotNull PsiExpression expr);
}