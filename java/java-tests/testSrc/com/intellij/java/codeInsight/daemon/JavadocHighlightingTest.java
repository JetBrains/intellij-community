// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.codeInspection.javaDoc.MissingJavadocInspection;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.paths.UrlReference;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavadocHighlightingTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk9());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), () -> List.of(),
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/javaDoc/"));

  private JavadocDeclarationInspection myDeclarationJavadocInspection;
  private MissingJavadocInspection myMissingJavadocInspection;

  @BeforeEach
  void setUp() {
    myDeclarationJavadocInspection = new JavadocDeclarationInspection();
    myDeclarationJavadocInspection.IGNORE_THROWS_DUPLICATE = false;
    myDeclarationJavadocInspection.IGNORE_DEPRECATED_ELEMENTS = true;

    myMissingJavadocInspection = new MissingJavadocInspection();
    myMissingJavadocInspection.IGNORE_DEPRECATED_ELEMENTS = true;
    myMissingJavadocInspection.PACKAGE_SETTINGS.MINIMAL_VISIBILITY = "public";
    myMissingJavadocInspection.PACKAGE_SETTINGS.setTagRequired("@author", true);
    myMissingJavadocInspection.FIELD_SETTINGS.ENABLED = false;
    myMissingJavadocInspection.TOP_LEVEL_CLASS_SETTINGS.ENABLED = false;
    myMissingJavadocInspection.INNER_CLASS_SETTINGS.ENABLED = false;
    daemonFixture.get().getFixture()
      .enableInspections(myDeclarationJavadocInspection, myMissingJavadocInspection, new JavaDocReferenceInspection());
  }

  @Test public void testJavadocPeriod() { myDeclarationJavadocInspection.IGNORE_PERIOD_PROBLEM = false; doTest(); }
  @Test public void testJavadocPeriod1() { myDeclarationJavadocInspection.IGNORE_PERIOD_PROBLEM = false; doTest(); }
  @Test public void testJavadocPeriod2() { myDeclarationJavadocInspection.IGNORE_PERIOD_PROBLEM = false; doTest(); }
  @Test public void testInlineTagAsDescription() { doTest(); }
  @Test public void testParam0() { doTest(); }
  @Test public void testParam1() { doTest(); }
  @Test public void testParam2() { doTest(); }
  @Test public void testParam3() { doTest(); }
  @Test public void testParam4() { doTest(); }
  @Test public void testRecordParamJava16() { doTest(); }
  @Test
  public void testTypeParam() {
    myMissingJavadocInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "private";
    myMissingJavadocInspection.METHOD_SETTINGS.setTagRequired("@param", true);
    doTest();
  }
  @Test public void testSee0() { doTest(); }
  @Test public void testSee1() { doTest(); }
  @Test public void testSee2() { doTest(); }
  @Test public void testSee3() { doTest(); }
  @Test public void testSee4() { doTest(); }
  @Test public void testSee5() { doTest(); }
  @Test public void testSee6() { doTest(); }
  @Test public void testLinkToItself() { doTest(); }
  @Test public void testLinkToMethodNoParams() { doTest(); }
  @Test public void testLinkToObjectMethods() { doTest(); }
  @Test public void testSeeConstants() { doTest(); }
  @Test public void testSeeNonRefs() { doTest(); }
  @Test public void testFragments() { doTest(); }
  @Test public void testReturn0() { doTest(); }
  @Test public void testReturn1() { doTest(); }
  @Test public void testException0() { doTest(); }
  @Test public void testException1() { doTest(); }
  @Test public void testException2() { doTest(); }
  @Test public void testException3() { doTest(); }
  @Test public void testException4() { myMissingJavadocInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "package"; doTest(); }
  @Test public void testInheritJavaDoc() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_3); doTest(); }
  @Test public void testLinkFromInnerClassToSelfMethod() { doTest(); }
  @Test public void testValueBadReference() { doTest(); }
  @Test public void testValueGoodReference() { doTest(); }
  @Test public void testValueReference14() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_4); doTest(); }
  @Test public void testValueEmpty() { doTest(); }
  @Test public void testValueNotOnField() { doTest(); }
  @Test public void testValueNotOnStaticField() { doTest(); }
  @Test public void testValueOnNotInitializedField() { doTest(); }
  @Test public void testEnumValueOfReference() { doTest(); }
  @Test public void testPackageInfo1() { doTest("packageInfo/p1/package-info.java"); }
  @Test public void testPackageInfo2() { doTest("packageInfo/p2/package-info.java"); }
  @Test public void testPackageInfo3() { doTest("packageInfo/p3/package-info.java"); }
  @Test public void testPackageInfo4() { doTest("packageInfo/p4/package-info.java"); }
  @Test public void testPackageInfo5() { doTest("packageInfo/p5/package-info.java"); }
  @Test
  public void testPackageInfo5ButWeHaveAnOldJavaVersion() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest("packageInfo/p5OldJava/package-info.java");
  }
  @Test public void testJava18Tags() { doTest(); }
  @Test public void testJava19Tags() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_9); doTest(); }
  @Test public void testJava12Tags() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_12); doTest(); }
  @Test
  public void testModuleInfoTags() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_9);
    doTest("moduleInfo/m1/module-info.java");
  }
  @Test
  public void testDeprecatedModule() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_9);
    doTest("moduleInfo/m2/module-info.java");
  }
  @Test public void testUnknownInlineTag() { doTest(); }
  @Test public void testUnknownTags() { doTest(); }
  @Test public void testVararg() { doTest(); }
  @Test public void testInnerClassReferenceInSignature() { doTest(); }
  @Test public void testBadReference() { doTest(); }
  @Test public void testMissingReturnDescription() { doTest(); }
  @Test public void testDoubleParenthesesInCode() { doTest(); }
  @Test public void testDuplicateParam0() { doTest(); }
  @Test public void testDuplicateParam1() { doTest(); }
  @Test public void testDuplicateParam2() { doTest(); }
  @Test public void testDuplicateReturn() { doTest(); }
  @Test public void testDuplicateDeprecated() { myDeclarationJavadocInspection.IGNORE_DEPRECATED_ELEMENTS = false; doTest(); }
  @Test public void testDuplicateSerial() { doTest(); }
  @Test public void testDuplicateThrows() { doTest(); }
  @Test public void testMissedTags() { doTest(); }
  @Test public void testDoubleMissedTags() { doTest(); }
  @Test public void testMissedThrowsTag() { myMissingJavadocInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "package"; doTest(); }
  @Test public void testMisplacedThrowsTag() { doTest(); }
  @Test public void testGenericsParams() { doTest(); }
  @Test public void testEnumConstructor() { myMissingJavadocInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = "package"; doTest(); }
  @Test public void testIgnoreDuplicateThrows() { myDeclarationJavadocInspection.IGNORE_THROWS_DUPLICATE = true; doTest(); }
  @Test public void testIgnoreAccessors() { myMissingJavadocInspection.IGNORE_ACCESSORS = true; doTest(); }
  @Test public void testAuthoredMethod() { doTest(); }
  @Test public void testThrowsInheritDoc() { doTest(); }
  @Test public void testSnippetInlineTag() {  doTest(); }
  @Test public void testSnippet() { doTest(); }
  @Test public void testSnippetMethod() { doTest(); }
  @Test public void testSnippetInstructions() { doTest(); }
  @Test public void testEmptySnippet() { doTest(); }
  @Test public void testOnlyEmptyLinesInSnippet() { doTest(); }
  @Test public void testSnippetInstructionsWithUnhandledThrowable() { doTest(); }
  @Test public void testSnippetInlineTagMarkdown() { doTest(); }
  @Test public void testSnippetInstructionsMarkdown() { doTest(); }
  @Test public void testUnalignedLeadingAsterisks() { doTest(); }
  @Test public void testParameterizedTypes() { doTest(); }
  @Test public void testParameterizedTypes02() { doTest(); }

  @Test
  public void testIssueLinksInJavaDoc() {
    IssueNavigationConfiguration navigationConfiguration =
      IssueNavigationConfiguration.getInstance(daemonFixture.get().getFixture().getProject());
    List<IssueNavigationLink> oldLinks = navigationConfiguration.getLinks();
    try {
      navigationConfiguration.setLinks(List.of(new IssueNavigationLink("ABC-\\d+", "http://example.com/$0"),
                                               new IssueNavigationLink("INVALID-\\d+", "http://example.com/$0/$1")));
      PsiFile file = daemonFixture.get().configureByFile(daemonFixture.get().getFixture().getTestName(false) + ".java");
      List<String> expected =
        List.of("http://example.com/ABC-1123", "http://example.com/ABC-2", "http://example.com/ABC-22", "http://example.com/ABC-11");
      ReadAction.runBlocking(() -> {
        if (Registry.is("ide.symbol.url.references")) {
          List<UrlReference> refs = PlatformTestUtil.collectUrlReferences(file);
          assertEquals(expected, ContainerUtil.map(refs, UrlReference::getUrl));
        }
        else {
          List<WebReference> refs = PlatformTestUtil.collectWebReferences(file);
          assertTrue(ContainerUtil.and(refs, PsiReferenceBase::isSoft));
          assertEquals(expected, ContainerUtil.map(refs, WebReference::getUrl));
        }
      });
    }
    finally {
      navigationConfiguration.setLinks(oldLinks);
    }
  }

  @Test
  public void testLinksInJavaDoc() {
    PsiFile file = daemonFixture.get().configureByFile(daemonFixture.get().getFixture().getTestName(false) + ".java");
    Set<String> expected = ContainerUtil.newHashSet(
      "http://www.unicode.org/unicode/standard/standard.html",
      "http://docs.oracle.com/javase/7/docs/tech-notes/guides/lang/cl-mt.html",
      "https://youtrack.jetbrains.com/issue/IDEA-131621",
      "mailto:webmaster@jetbrains.com");
    ReadAction.runBlocking(() -> {
      if (Registry.is("ide.symbol.url.references")) {
        List<UrlReference> refs = PlatformTestUtil.collectUrlReferences(file);
        assertEquals(expected, refs.stream().map(PsiSymbolReference::getReferenceText).collect(Collectors.toSet()));
      }
      else {
        List<WebReference> refs = PlatformTestUtil.collectWebReferences(file);
        assertTrue(ContainerUtil.and(refs, PsiReferenceBase::isSoft));
        assertEquals(expected, refs.stream().map(PsiReferenceBase::getCanonicalText).collect(Collectors.toSet()));
      }
    });
  }

  private void doTest() {
    doTest(daemonFixture.get().getFixture().getTestName(false) + ".java");
  }

  private void doTest(String testFileName) {
    daemonFixture.get().doTest(testFileName, true, false);
  }
}