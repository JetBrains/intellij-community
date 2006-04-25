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
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference in Java code used as part of an expression.
 */
public interface PsiReferenceExpression extends PsiExpression, PsiJavaCodeReferenceElement {
  /**
   * Returns the expression used as the qualifier of the reference (the content up to the
   * last period).
   *
   * @return the qualifier, or null if the reference is not qualified.
   */
  @Nullable
  PsiExpression getQualifierExpression();

  /**
   * Creates an <code>import static</code> statement importing the referenced member
   * from the specified class, or qualifies the reference with the class name if
   * that class is already imported by a regular import statement.
   *
   * @param qualifierClass the class to import.
   * @return the element corresponding to this element in the PSI tree after the modification.
   * @throws IncorrectOperationException if the modification failed for some reason.
   */
  PsiElement bindToElementViaStaticImport(PsiClass qualifierClass) throws IncorrectOperationException ;

  void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException;
}
