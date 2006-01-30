/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

/**
 * @author ven
 */
public abstract class ExpectedTypesProvider {
  public static ExpectedTypesProvider getInstance(Project project) {
    return project.getComponent(ExpectedTypesProvider.class);
  }

  public abstract ExpectedTypeInfo createInfo(PsiType type, int kind, PsiType defaultType, int tailType);

  public abstract ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr, boolean forCompletion);

  public abstract ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr,
                                                    boolean forCompletion,
                                                    ExpectedClassProvider classProvider);

  public abstract PsiType[] processExpectedTypes(ExpectedTypeInfo[] infos,
                                          PsiTypeVisitor<PsiType> visitor, Project project);

  /**
   * Finds fields and methods of specified name whenever corresponding reference has been encountered.
   * By default searhes in the global scope (see ourGlobalScopeClassProvider), but caller can provide its own algorithm e.g. to narrow search scope
   */
  public interface ExpectedClassProvider {
    PsiField[] findDeclaredFields(final PsiManager manager, String name);

    PsiMethod[] findDeclaredMethods(final PsiManager manager, String name);
  }

}
