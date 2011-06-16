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
package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface MethodImplementor {
  ExtensionPointName<MethodImplementor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.methodImplementor");

  @NotNull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);

  @NotNull
  PsiMethod[] createImplementationPrototypes(final PsiClass inClass, PsiMethod method) throws IncorrectOperationException;

  boolean isBodyGenerated();

  GenerationInfo createGenerationInfo(PsiMethod method);
}
