package com.intellij.refactoring;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class TypeMigrationByThreadLocalRuleTest extends TypeMigrationTestBase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/typeMigrationByThreadLocal/";
  }


  public void testDirectInt() {
    doTestFieldType("i", getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.Integer>", null));
  }
  
  public void testDirectByte() {
    doTestFieldType("i", getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.Byte>", null));
  }

  public void testDirectString() {
    doTestFieldType("myS", getElementFactory().createTypeFromText("java.lang.ThreadLocal<java.lang.String>", null));
  }

  public void testLanguageLevel() {
    IdeaTestUtil.withLevel(myModule, LanguageLevel.JDK_1_3, () -> doTestFieldType("i", getElementFactory().createTypeFromText("java.lang.ThreadLocal", null)));
  }
}