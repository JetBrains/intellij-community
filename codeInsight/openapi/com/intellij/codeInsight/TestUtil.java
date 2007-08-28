/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class TestUtil {
  public static final ExtensionPointName<TestFramework> TEST_FRAMEWORK = ExtensionPointName.create("com.intellij.testFramework");

  private TestUtil() {}

  public static boolean isTestClass(final PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TEST_FRAMEWORK);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestKlass(psiClass)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static PsiMethod findSetUpMethod(final PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TEST_FRAMEWORK);
    for (TestFramework framework : testFrameworks) {
      final PsiMethod setUpMethod;
      try {
        setUpMethod = framework.findSetUpMethod(psiClass);
        if (setUpMethod != null) {
          return setUpMethod;
        }
      }
      catch (IncorrectOperationException e) {
        //skip
      }
    }
    return null;
  }
}