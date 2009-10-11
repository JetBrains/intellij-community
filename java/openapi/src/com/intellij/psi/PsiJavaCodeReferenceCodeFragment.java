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
package com.intellij.psi;

/**
 * Represents a fragment of Java code the contents of which is a reference element
 * referencing a Java class or package.
 *
 * @author ven
 * @see PsiElementFactory#createReferenceCodeFragment(String, PsiElement, boolean, boolean)
 */
public interface PsiJavaCodeReferenceCodeFragment extends JavaCodeFragment {
  /**
   * Returns the reference contained in the fragment.
   *
   * @return the reference element instance.
   */
  PsiJavaCodeReferenceElement getReferenceElement();

  /**
   * Checks if classes are accepted as the target of the reference.
   *
   * @return if true then classes as well as packages are accepted as reference target,
   * otherwise only packages are.
   */
  boolean isClassesAccepted();
}
