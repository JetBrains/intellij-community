/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.psi.*;

/**
 * Interface for IsNull, IsNotNull Method Checks and Assert True/False/IsNull/IsNotNull Method Checks to be performed by the Constant Condition Inspection.
 * These Checkers allow the user to specify that the method in question performs some type of validation on the parameter passed into the method.
 * For example, if the method is defined as performing an IsNotNull Check, and variable x is passed into the method, then all code after the method call
 * will assume that x is Not Null.
 *
 * @author <a href="mailto:johnnyclark@gmail.com">Johnny Clark</a>
 *         Creation Date: 8/14/12
 */
public interface ConditionChecker {
  enum Type {
    IS_NULL_METHOD("IsNull Method"),
    IS_NOT_NULL_METHOD("IsNotNull Method"),
    ASSERT_IS_NULL_METHOD("Assert IsNull Method"),
    ASSERT_IS_NOT_NULL_METHOD("Assert IsNotNull Method"),
    ASSERT_TRUE_METHOD("Assert True Method"),
    ASSERT_FALSE_METHOD("Assert False Method");
    private final String myStringRepresentation;

    Type(String stringRepresentation) {
      myStringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
      return myStringRepresentation;
    }
  }

  boolean matches(PsiMethod psiMethod);
  boolean matches(PsiMethod psiMethod, int paramIndex);

  boolean overlaps(ConditionChecker checker);

  Type getType();
}
