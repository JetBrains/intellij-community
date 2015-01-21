/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class JavadocHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    JavaDocLocalInspection localInspection = new JavaDocLocalInspection();
    localInspection.setIgnoreDuplicatedThrows(false);
    return new LocalInspectionTool[]{
      localInspection,
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

  public void testInlineTagAsDescription() throws Exception { doTest(); }

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
  public void testSee6() throws Exception { doTest(); }
  public void testSeeConstants() throws Exception { doTest(); }
  public void testReturn0() throws Exception { doTest(); }
  public void testException0() throws Exception { doTest(); }
  public void testException1() throws Exception { doTest(); }
  public void testException2() throws Exception { doTest(); }
  public void testException3() throws Exception { doTest(); }
  public void testException4() throws Exception { 
    final JavaDocLocalInspection javaDocLocalInspection = new JavaDocLocalInspection();
    javaDocLocalInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    enableInspectionTool(javaDocLocalInspection);
    doTest(); 
  }
  public void testMultipleThrows() throws Exception { doTest(); }
  public void testInheritJavaDoc() throws Exception {doTestWithLangLevel(LanguageLevel.JDK_1_3);}
  public void testLink0() throws Exception { doTest(); }
  public void testLinkFromInnerClassToSelfMethod() throws Exception { doTest(); }

  public void testValueBadReference() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueGoodReference() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueReference14() throws Exception { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueEmpty() throws Exception { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueNotOnField() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueNotOnStaticField() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueOnNotInitializedField() throws Exception { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testJava18Tags() throws Exception { doTestWithLangLevel(LanguageLevel.JDK_1_8); }

  public void testUnknownInlineTag() throws Exception { doTest(); }
  public void testUnknownTags() throws Exception { doTest(); }

  public void testBadCharacters() throws Exception { doTest(); }

  public void testVararg() throws Exception { doTest(); }

  public void testInnerClassReferenceInSignature() throws Exception { doTest(); }

  public void testBadReference() throws Exception { doTest(); }

  public void testMissingReturnDescription() throws Exception { doTest(); }

  public void testDoubleParenthesesInCode() throws Exception {
    doTest();
  }

  private void doTestWithLangLevel(final LanguageLevel langLevel) throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(langLevel);
    doTest();
  }

  public void testLinksInJavaDoc() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final List<WebReference> refs = new ArrayList<WebReference>();
    myFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        for(PsiReference ref:element.getReferences()) {
          if (ref instanceof WebReference) refs.add((WebReference)ref);
        }

        super.visitElement(element);
      }
    });

    String[] targets = {"http://www.unicode.org/unicode/standard/standard.html",
      "http://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html",
      "https://youtrack.jetbrains.com/issue/IDEA-131621",
      "mailto:webmaster@jetbrains.com"
    };
    assertTrue(refs.size() == targets.length);
    int i = 0;

    for(WebReference ref:refs) {
      assertEquals(ref.getCanonicalText(), targets[i++]);
      assertTrue(ref.isSoft());
      assertNotNull(ref.resolve());
    }
  }

  protected void doTest() throws Exception {
    super.doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}