/*
 * User: anna
 * Date: 19-Aug-2009
 */
package com.intellij.refactoring;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class TypeMigrationByThreadLocalRuleTest extends TypeMigrationTestBase{
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/typeMigrationByThreadLocal/";
  }


  public void testDirectInt() throws Exception {
    doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.Integer>", null));
  }
  
  public void testDirectByte() throws Exception {
    doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.Byte>", null));
  }

  public void testDirectString() throws Exception {
    doTestFieldType("myS", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.String>", null));
  }

  public void testLanguageLevel() throws Exception {
    final LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel languageLevel = extension.getLanguageLevel();
    try {
      extension.setLanguageLevel(LanguageLevel.JDK_1_3);
      doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal", null));
    }
    finally {
      extension.setLanguageLevel(languageLevel);
    }
  }
}