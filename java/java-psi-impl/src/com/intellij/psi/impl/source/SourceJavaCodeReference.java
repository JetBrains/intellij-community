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
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

/**
 * This interface should be implemented by all PsiJavaCodeReference implementations
 * in source.
 * 
 * @author dsl
 */
public interface SourceJavaCodeReference {
  /**
   * @return text of class name (as much as there is in reference text, that is
   *      with qualifications if they are present)
   */
  String getClassNameText();

  /**
   * Helper method for ReferenceAdjuster. Tries to qualify this reference as if
   * it references <code>targetClass</code>. Does not check that it indeed references
   * targetClass
   * @param targetClass
   */
  void fullyQualify(PsiClass targetClass);

  boolean isQualified();

  PsiElement getQualifier();

}
