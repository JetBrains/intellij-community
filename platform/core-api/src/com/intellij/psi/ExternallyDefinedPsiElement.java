/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;


/**
 * Interface for PSI elements which may be injected into other elements but physically belongs
 * to other file - like AspectJ inter-type fields/methods.
 */
public interface ExternallyDefinedPsiElement extends PsiElement {
  /**
   * If inspection started for files with injections founds any problem in them (or their child)
   * it should be able to display them locally. This method allows to define such substitution element.
   * E.g. it may be a class name identifier element for fields/methods injected in that class.<br/>
   * See <code>ProblemsHolder.redirectProblem()</code> for details.
   *
   * @return PSI element to which problem descriptions should be redirected
   */
  @Nullable
  PsiElement getProblemTarget();
}
