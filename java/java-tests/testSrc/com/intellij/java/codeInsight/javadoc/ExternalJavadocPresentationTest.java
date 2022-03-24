// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class ExternalJavadocPresentationTest extends LightJavaCodeInsightTestCase {
  private static final String TEST_ROOT = "/codeInsight/externalJavadoc/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testStringClass() {
    doTest("", "String/7/page.html", "String/7/expected.html");
    doTest("", "String/6/page.html", "String/6/expected.html");
  }

  public void testToLowerCase() {
    doTest("lang/String.html#toLowerCase()", "String/7/page.html", "String/7/expectedToLowerCase.html");
    doTest("lang/String.html#toLowerCase()", "String/6/page.html", "String/6/expectedToLowerCase.html");
    doTest("lang/String.html#toLowerCase()", "String/16/page.html", "String/16/expectedToLowerCase.html");
  }

  public void testListAdd() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest("List.html#add-E-", "List/8/page.html", "List/8/expectedAdd.html"));
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_17, () -> doTest("util/List.html#add(java.lang.Object)", "List/17/page.html", "List/17/expectedAdd.html"));
  }

  public void testInvalidJavadoc() {
    doTest("", "String/invalid/page.html", "String/invalid/expected.html", false);
  }

  public void testPackageSummary() {
    doTest("java/lang/package-summary.html", "packageSummary/util/page.html", "packageSummary/util/expected.html");
  }

  public void testNoMethodsOrFieldsInClass() {
    doTest("SimpleInterface.html", "noMethodsOrFieldsInClass/SimpleInterface.html", "noMethodsOrFieldsInClass/expected.html");
  }

  public void testPackageSummaryJava8() {
    doTest("package-summary.html", "packageSummaryJava8/package-summary.html", "packageSummaryJava8/expected.html");
  }

  private void doTest(String url, String pageText, String expected) {
    doTest(url, pageText, expected, true);
  }

  private void doTest(String url, String testFile, String expectedFile, boolean matchStart) {
    class JavadocExternalTestFilter extends JavaDocExternalFilter {
      JavadocExternalTestFilter(Project project) {
        super(project);
      }

      public void test(String url, Reader input, StringBuilder data, boolean matchStart) {
        try {
          super.doBuildFromStream(url, input, data, false, matchStart);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    String text = loadFile(testFile);
    JavadocExternalTestFilter filter = new JavadocExternalTestFilter(getProject());
    StringBuilder extractedData = new StringBuilder();
    filter.test(url, new StringReader(text), extractedData, true);
    if (!matchStart) {
      assertEmpty(extractedData.toString());
      filter.test(url, new StringReader(text), extractedData, false);
    }

    assertEquals(loadFile(expectedFile), extractedData.toString());
  }

  private String loadFile(String testFile) {
    try {
      return StringUtil.convertLineSeparators(FileUtil.loadFile(new File(getTestDataPath() + TEST_ROOT, testFile)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}