/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiClass;

public class TestUtil {
  public static final ExtensionPointName<TestFramework> TEST_FRAMEWORK = ExtensionPointName.create("com.intellij.testFramework");

  private TestUtil() {}

  public static boolean isTestClass(PsiClass psiClass) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TEST_FRAMEWORK);
    for (TestFramework framework : testFrameworks) {
      if (framework.isTestKlass(psiClass)) {
        return true;
      }
    }
    return false;
  }

}