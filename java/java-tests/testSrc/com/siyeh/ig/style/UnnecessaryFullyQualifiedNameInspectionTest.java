// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.IGInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryFullyQualifiedNameInspectionTest extends IGInspectionTestCase {
  private static final String BASE_DIR = "com/siyeh/igtest/style/";

  public void testFqnInJavadoc_Unnecessary_WhenFullyQualifyIfNotImported() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fqn_javadoc_fully_qualify_if_not_imported", JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED);
  }

  public void testFqnInJavadoc_Unnecessary_WhenShortNamesAlways() {
    final PsiClass aClass =
      JavaPsiFacade.getInstance(getProject()).findClass("java.lang.Appendable", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass); // test needs this class to be present
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fully_qualified_name/", JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT);
  }

  public void testAcceptFqnInJavadoc() {
    doTestWithFqnInJavadocSetting(BASE_DIR + "unnecessary_fully_qualified_name_accept_in_javadoc/", JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS);
  }

  public void testConflictWithTypeParameter() {
    doTest(BASE_DIR + "unnecessary_fqn_type_parameter_conflict", new UnnecessaryFullyQualifiedNameInspection());
  }

  public void testSkipWarningIfThereIsSameNames() {
    doTest(BASE_DIR + "unnecessary_fqn_skip_warn_if_confusing", new UnnecessaryFullyQualifiedNameInspection());
  }

  private void doTestWithFqnInJavadocSetting(String dirPath, int classNamesInJavadoc) {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

    javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
    doTest(dirPath, new UnnecessaryFullyQualifiedNameInspection());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}
