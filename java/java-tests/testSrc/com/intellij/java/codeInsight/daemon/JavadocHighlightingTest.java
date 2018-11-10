// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JavadocHighlightingTest extends LightDaemonAnalyzerTestCase {
  private JavaDocLocalInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInspection = new JavaDocLocalInspection();
    myInspection.setIgnoreDuplicatedThrows(false);
    myInspection.IGNORE_DEPRECATED = true;
    myInspection.setPackageOption("public", "@author");
    enableInspectionTools(myInspection, new JavaDocReferenceInspection());
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/javaDoc/";
  }

  public void testJavadocPeriod() { myInspection.IGNORE_JAVADOC_PERIOD = false; doTest(); }
  public void testJavadocPeriod1() { myInspection.IGNORE_JAVADOC_PERIOD = false; doTest(); }
  public void testJavadocPeriod2() { myInspection.IGNORE_JAVADOC_PERIOD = false; doTest(); }
  public void testInlineTagAsDescription() { doTest(); }
  public void testParam0() { doTest(); }
  public void testParam1() { doTest(); }
  public void testParam2() { doTest(); }
  public void testParam3() { doTest(); }
  public void testParam4() { doTest(); }
  public void testTypeParam() {
    myInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "private"; 
    myInspection.METHOD_OPTIONS.REQUIRED_TAGS = "@param"; 
    doTest(); 
  }
  public void testSee0() { doTest(); }
  public void testSee1() { doTest(); }
  public void testSee2() { doTest(); }
  public void testSee3() { doTest(); }
  public void testSee4() { doTest(); }
  public void testSee5() { doTest(); }
  public void testSee6() { doTest(); }
  public void testLinkToItself() { doTest(); }
  public void testSeeConstants() { doTest(); }
  public void testSeeNonRefs() { doTest(); }
  public void testReturn0() { doTest(); }
  public void testException0() { doTest(); }
  public void testException1() { doTest(); }
  public void testException2() { doTest(); }
  public void testException3() { doTest(); }
  public void testException4() { myInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package"; doTest(); }
  public void testInheritJavaDoc() { setLanguageLevel(LanguageLevel.JDK_1_3); doTest(); }
  public void testLink0() { doTest(); }
  public void testLinkFromInnerClassToSelfMethod() { doTest(); }
  public void testValueBadReference() { doTest(); }
  public void testValueGoodReference() { doTest(); }
  public void testValueReference14() { setLanguageLevel(LanguageLevel.JDK_1_4); doTest(); }
  public void testValueEmpty() { doTest(); }
  public void testValueNotOnField() { doTest(); }
  public void testValueNotOnStaticField() { doTest(); }
  public void testValueOnNotInitializedField() { doTest(); }
  public void testEnumValueOfReference() { doTest(); }
  public void testPackageInfo1() { doTest("packageInfo/p1/package-info.java"); }
  public void testPackageInfo2() { doTest("packageInfo/p2/package-info.java"); }
  public void testPackageInfo3() { doTest("packageInfo/p3/package-info.java"); }
  public void testPackageInfo4() { doTest("packageInfo/p4/package-info.java"); }
  public void testJava18Tags() { doTest(); }
  public void testJava19Tags() { setLanguageLevel(LanguageLevel.JDK_1_9); doTest(); }
  public void testModuleInfoTags() { setLanguageLevel(LanguageLevel.JDK_1_9); doTest("moduleInfo/m1/module-info.java"); }
  public void testDeprecatedModule() { setLanguageLevel(LanguageLevel.JDK_1_9); doTest("moduleInfo/m2/module-info.java"); }
  public void testUnknownInlineTag() { doTest(); }
  public void testUnknownTags() { doTest(); }
  public void testBadCharacters() { doTest(); }
  public void testVararg() { doTest(); }
  public void testInnerClassReferenceInSignature() { doTest(); }
  public void testBadReference() { doTest(); }
  public void testMissingReturnDescription() { doTest(); }
  public void testDoubleParenthesesInCode() { doTest(); }
  public void testDuplicateParam() { doTest(); }
  public void testDuplicateReturn() { doTest(); }
  public void testDuplicateDeprecated() { myInspection.IGNORE_DEPRECATED = false; doTest(); }
  public void testDuplicateSerial() { doTest(); }
  public void testDuplicateThrows() { doTest(); }
  public void testMissedTags() { doTest(); }
  public void testDoubleMissedTags() { doTest(); }
  public void testMissedThrowsTag() { myInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package"; doTest(); }
  public void testMisplacedThrowsTag() { doTest(); }
  public void testGenericsParams() { doTest(); }
  public void testEnumConstructor() { myInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package"; doTest(); }
  public void testIgnoreDuplicateThrows() { myInspection.setIgnoreDuplicatedThrows(true); doTest(); }
  public void testIgnoreAccessors() { myInspection.setIgnoreSimpleAccessors(true); doTest(); }
  public void testAuthoredMethod() { doTest(); }

  public void testIssueLinksInJavaDoc() {
    IssueNavigationConfiguration navigationConfiguration = IssueNavigationConfiguration.getInstance(getProject());
    List<IssueNavigationLink> oldLinks = navigationConfiguration.getLinks();
    try {
      navigationConfiguration.setLinks(ContainerUtil.newArrayList(new IssueNavigationLink("ABC-\\d+", "http://example.com/$0"),
                                                                  new IssueNavigationLink("INVALID-\\d+", "http://example.com/$0/$1")));
      configureByFile(getTestName(false) + ".java");
      List<String> expected = ContainerUtil.newArrayList(
        "http://example.com/ABC-1123", "http://example.com/ABC-2", "http://example.com/ABC-22", "http://example.com/ABC-11");
      List<WebReference> refs = PlatformTestUtil.collectWebReferences(myFile);
      assertTrue(refs.stream().allMatch(PsiReferenceBase::isSoft));
      assertEquals(expected, refs.stream().map(WebReference::getUrl).collect(Collectors.toList()));
    }
    finally {
      navigationConfiguration.setLinks(oldLinks);
    }
  }

  public void testLinksInJavaDoc() {
    configureByFile(getTestName(false) + ".java");
    Set<String> expected = ContainerUtil.newHashSet(
      "http://www.unicode.org/unicode/standard/standard.html",
      "http://docs.oracle.com/javase/7/docs/tech-notes/guides/lang/cl-mt.html",
      "https://youtrack.jetbrains.com/issue/IDEA-131621",
      "mailto:webmaster@jetbrains.com");
    List<WebReference> refs = PlatformTestUtil.collectWebReferences(myFile);
    assertTrue(refs.stream().allMatch(PsiReferenceBase::isSoft));
    assertEquals(expected, refs.stream().map(PsiReferenceBase::getCanonicalText).collect(Collectors.toSet()));
  }

  private void doTest() {
    doTest(getTestName(false) + ".java");
  }

  private void doTest(String testFileName) {
    doTest(testFileName, true, false);
  }
}