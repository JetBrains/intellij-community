// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElementFactory;

/**
 * @author Réda Housni Alaoui
 */
public class TypeMigrationAutoboxingTest extends TypeMigrationTestBase {

  private PsiElementFactory myFactory;

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/typeMigrationAutoboxing/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    myFactory = getElementFactory();
  }

  @Override
  public void tearDown() throws Exception {
    myFactory = null;

    super.tearDown();
  }

  @Override
  protected boolean autoBox() {
    return true;
  }

  public void testIntToLong() {
    doTestMethodType("bar", myFactory.createTypeByFQClassName(Long.class.getCanonicalName()));
  }
}
