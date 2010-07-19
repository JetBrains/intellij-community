package com.intellij.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;


public class JavadocHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new JavaDocLocalInspection(),
      new JavaDocReferenceInspection()
    };
  }

  public void testJavadocPeriod() throws Exception {
    final JavaDocLocalInspection javaDocLocalInspection = new JavaDocLocalInspection();
    javaDocLocalInspection.IGNORE_JAVADOC_PERIOD = false;
    enableInspectionTool(javaDocLocalInspection);
    doTest();
  }

  public void testJavadocPeriod1() throws Exception {
    final JavaDocLocalInspection javaDocLocalInspection = new JavaDocLocalInspection();
    javaDocLocalInspection.IGNORE_JAVADOC_PERIOD = false;
    enableInspectionTool(javaDocLocalInspection);
    doTest();
  }

  public void testJavadocPeriod2() throws Exception {
    final JavaDocLocalInspection javaDocLocalInspection = new JavaDocLocalInspection();
    javaDocLocalInspection.IGNORE_JAVADOC_PERIOD = false;
    enableInspectionTool(javaDocLocalInspection);
    doTest();
  }

  public void testInlineTagAsDescription() throws Exception {
    doTest();
  }

  public void testParam0() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testParam1() throws Exception { doTest(); }
  public void testParam2() throws Exception { doTest(); }
  public void testParam3() throws Exception { doTest(); }
  public void testParam4() throws Exception { doTest(); }
  public void testSee0() throws Exception { doTest(); }
  public void testSee1() throws Exception { doTest(); }
  public void testSee2() throws Exception { doTest(); }
  public void testSee3() throws Exception { doTest(); }
  public void testSee4() throws Exception { doTest(); }
  public void testSee5() throws Exception { doTest(); }
  public void testSee6() throws Exception {doTest();}
  public void testSeeConstants() throws Exception { doTest();}
  public void testReturn0() throws Exception { doTest(); }
  public void testException0() throws Exception { doTest(); }
  public void testException1() throws Exception { doTest(); }
  public void testException2() throws Exception { doTest(); }
  public void testException3() throws Exception { doTest(); }
  public void testException4() throws Exception { doTest(); }
  public void testMultipleThrows() throws Exception { doTest(); }
  public void testInheritJavaDoc() throws Exception {doTestWithLangLevel(LanguageLevel.JDK_1_3);}
  public void testLink0() throws Exception { doTest(); }
  public void testLinkFromInnerClassToSelfMethod() throws Exception {doTest();}

  public void testValueBadReference() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueGoodReference() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueReference14() throws Exception { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueEmpty() throws Exception { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueNotOnField() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueNotOnStaticField() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueOnNotInitializedField() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }

  public void testUnknownInlineTag() throws Exception {doTest();}
  public void testUnknownTags() throws Exception {doTest();}

  public void testBadCharacters() throws Exception {doTest();}

  public void testVararg() throws Exception {doTest();}

  public void testInnerClassReferenceInSignature() throws Exception {doTest();}

  public void testBadReference() throws Exception{
    doTest();
  }

  public void testMissingReturnDescription() throws Exception {doTest();}

  private void doTestWithLangLevel(final LanguageLevel langLevel) throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(langLevel);
    doTest();
  }

  protected void doTest() throws Exception {
    super.doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
}