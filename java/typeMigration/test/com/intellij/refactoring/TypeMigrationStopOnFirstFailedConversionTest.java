// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiType;

/**
 * @author Réda Housni Alaoui
 */
public class TypeMigrationStopOnFirstFailedConversionTest extends TypeMigrationTestBase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/typeMigrationStopOnFirstFailedConversion/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
  }

  @Override
  protected boolean stopOnFirstFailedConversion() {
    return true;
  }

  public void testTwoFailedConversions() {
    doTestFirstParamType("foo", PsiType.DOUBLE);
  }
}
