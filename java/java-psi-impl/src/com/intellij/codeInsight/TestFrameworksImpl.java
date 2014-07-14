/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class TestFrameworksImpl extends TestFrameworks {
  private TestFrameworksImpl() {
  }

  @Override
  public boolean isTestClass(final PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestClass(psiClass)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPotentialTestClass(PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.isPotentialTestClass(psiClass)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nullable
  public PsiMethod findOrCreateSetUpMethod(final PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestClass(psiClass)) {
        try {
          final PsiMethod setUpMethod = (PsiMethod)framework.findOrCreateSetUpMethod(psiClass);
          if (setUpMethod != null) {
            return setUpMethod;
          }
        }
        catch (IncorrectOperationException e) {
          //skip
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public PsiMethod findSetUpMethod(final PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestClass(psiClass)) {
        final PsiMethod setUpMethod = (PsiMethod)framework.findSetUpMethod(psiClass);
        if (setUpMethod != null) {
          return setUpMethod;
        }
      }
    }
    return null;
  }
  
  @Override
  @Nullable
  public PsiMethod findTearDownMethod(final PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestClass(psiClass)) {
        final PsiMethod setUpMethod = (PsiMethod)framework.findTearDownMethod(psiClass);
        if (setUpMethod != null) {
          return setUpMethod;
        }
      }
    }
    return null;
  }

  @Override
  protected boolean hasConfigMethods(PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.findSetUpMethod(psiClass) != null || framework.findTearDownMethod(psiClass) != null) return true;
    }
    return false;
  }

  @Override
  public boolean isTestMethod(PsiMethod method) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestMethod(method)) return true;
    }
    return false;
  }
}