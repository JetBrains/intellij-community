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
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JavadocHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/javaDoc";

  private JavaDocLocalInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInspection = new JavaDocLocalInspection();
    myInspection.setIgnoreDuplicatedThrows(false);
    enableInspectionTools(myInspection, new JavaDocReferenceInspection());
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testJavadocPeriod() { myInspection.IGNORE_JAVADOC_PERIOD = false; doTest(); }
  public void testJavadocPeriod1() { myInspection.IGNORE_JAVADOC_PERIOD = false; doTest(); }
  public void testJavadocPeriod2() { myInspection.IGNORE_JAVADOC_PERIOD = false; doTest(); }
  public void testInlineTagAsDescription() { doTest(); }
  public void testParam0() { doTestWithLangLevel(LanguageLevel.HIGHEST); }
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
  public void testSeeConstants() { doTest(); }
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
  public void testValueBadReference() { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueGoodReference() { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueReference14() { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueEmpty() { doTestWithLangLevel(LanguageLevel.JDK_1_4); }
  public void testValueNotOnField() { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueNotOnStaticField() { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testValueOnNotInitializedField() { doTestWithLangLevel(LanguageLevel.HIGHEST); }
  public void testJava18Tags() { doTestWithLangLevel(LanguageLevel.JDK_1_8); }
  public void testUnknownInlineTag() { doTest(); }
  public void testUnknownTags() { doTest(); }
  public void testBadCharacters() { doTest(); }
  public void testVararg() { doTest(); }
  public void testInnerClassReferenceInSignature() { doTest(); }
  public void testBadReference() { doTest(); }
  public void testMissingReturnDescription() { doTest(); }
  public void testDoubleParenthesesInCode() { doTest(); }

  public void testIssueLinksInJavaDoc() {
    IssueNavigationConfiguration navigationConfiguration = IssueNavigationConfiguration.getInstance(getProject());
    List<IssueNavigationLink> oldLinks = navigationConfiguration.getLinks();
    try {
      IssueNavigationLink link = new IssueNavigationLink("ABC-\\d+", "http://example.com/$0");
      navigationConfiguration.setLinks(ContainerUtil.<IssueNavigationLink>newArrayList(link));
      configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
      List<String> expected = ContainerUtil.newArrayList("http://example.com/ABC-1123", "http://example.com/ABC-2", 
                                                         "http://example.com/ABC-22", "http://example.com/ABC-11");
      List<String> actual = collectWebReferences().stream().map(WebReference::getUrl).collect(Collectors.toList());
      assertEquals(expected, actual);
    }
    finally {
      navigationConfiguration.setLinks(oldLinks);
    }
  }
  
  public void testLinksInJavaDoc() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    @SuppressWarnings("SpellCheckingInspection") Set<String> expected = ContainerUtil.newHashSet(
      "http://www.unicode.org/unicode/standard/standard.html",
      "http://docs.oracle.com/javase/7/docs/technotes/guides/lang/cl-mt.html",
      "https://youtrack.jetbrains.com/issue/IDEA-131621",
      "mailto:webmaster@jetbrains.com");
    Set<String> actual = collectWebReferences().stream().map(PsiReferenceBase::getCanonicalText).collect(Collectors.toSet());
    assertEquals(expected, actual);
  }

  @NotNull
  public static List<WebReference> collectWebReferences() {
    final List<WebReference> refs = new ArrayList<>();
    myFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        for (PsiReference ref : element.getReferences()) {
          if (ref instanceof WebReference) refs.add((WebReference)ref);
        }
        super.visitElement(element);
      }
    });
    assertTrue(refs.stream().allMatch(PsiReferenceBase::isSoft));
    return refs;
  }

  private void doTestWithLangLevel(LanguageLevel level) {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(level);
    doTest();
  }

  protected void doTest() {
    super.doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}