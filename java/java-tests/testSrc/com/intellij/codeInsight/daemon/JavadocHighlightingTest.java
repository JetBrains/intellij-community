/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiReferenceBase;
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
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    myInspection = new JavaDocLocalInspection();
    myInspection.setIgnoreDuplicatedThrows(false);
    enableInspectionTools(myInspection, new JavaDocReferenceInspection());
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
  public void testMultipleThrows() { doTest(); }
  public void testInheritJavaDoc() { doTestWithLangLevel(LanguageLevel.JDK_1_3); }
  public void testLink0() { doTest(); }
  public void testLinkFromInnerClassToSelfMethod() { doTest(); }
  public void testValueBadReference() { doTest(); }
  public void testValueGoodReference() { doTest(); }
  public void testValueReference14() { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueEmpty() { doTest(); }
  public void testValueNotOnField() { doTest(); }
  public void testValueNotOnStaticField() { doTest(); }
  public void testValueOnNotInitializedField() { doTest(); }
  public void testJava18Tags() { doTest(); }
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
  public void testDuplicateDeprecated() { doTest(); }
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

  private void doTestWithLangLevel(LanguageLevel level) {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(level);
    doTest();
  }

  protected void doTest() {
    super.doTest(getTestName(false) + ".java", true, false);
  }
}