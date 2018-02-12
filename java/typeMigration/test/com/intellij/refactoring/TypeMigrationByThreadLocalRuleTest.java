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


  public void testDirectInt() {
    doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.Integer>", null));
  }
  
  public void testDirectByte() {
    doTestFieldType("i", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.Byte>", null));
  }

  public void testDirectString() {
    doTestFieldType("myS", myJavaFacade.getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.String>", null));
  }

  public void testLanguageLevel() {
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