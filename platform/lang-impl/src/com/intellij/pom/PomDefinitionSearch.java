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
package com.intellij.pom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class PomDefinitionSearch implements QueryExecutor<PsiElement, PsiElement> {
  @Override
  public boolean execute(@NotNull PsiElement queryParameters, @NotNull Processor<PsiElement> consumer) {
    if (queryParameters instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)queryParameters).getTarget();
      if (target instanceof PsiTarget) {
        if (!consumer.process(((PsiTarget)target).getNavigationElement())) return false;
      }
    }
    return true;
  }
}
