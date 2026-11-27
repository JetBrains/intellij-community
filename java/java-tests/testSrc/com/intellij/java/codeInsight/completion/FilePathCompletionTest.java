// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.application.PathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaProjectBuilderCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FilePathCompletionTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaProjectBuilderCodeInsightFixture(PathManager.getHomePath(), (projectBuilder, fixture) -> {
      JavaModuleFixtureBuilder<?> moduleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
      moduleBuilder.addSourceContentRoot(fixture.getTempDirPath());
      String otherRoot = fixture.getTempDirPath() + "/otherRoot";
      assertTrue(new File(otherRoot).mkdirs());
      moduleBuilder.addSourceContentRoot(otherRoot);
    });

  @BeforeEach
  void setUp() {
    IdeaTestUtil.setProjectLanguageLevel(codeInsightFixture.get().getProject(), LanguageLevel.JDK_1_6);
  }

  @Test
  public void testCompletion() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("x/XXX.java", "");
    myFixture.addFileToProject("otherRoot/x/XXX.java", "");
    myFixture.addFileToProject("otherRoot/x/YYY.png", "");

    PsiFile file = myFixture.addFileToProject("x/C.java", "class C { String s = \"/x/<caret>\" }");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("C.java", "XXX.java", "YYY.png");
  }

  @Test
  public void testOverridesWordCompletion() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("/foo/bar.xml", "");
    myFixture.addFileToProject("/foo/boo.xml", "");
    myFixture.configureByText("a.java", """
      class A {
        String s1 = "foo/bar.xml";
        String s2 = "/foo/b<caret>";
      }""");
    assertNotNull(myFixture.completeBasic());
    assertThat(myFixture.getLookupElementStrings()).containsExactly("bar.xml", "boo.xml");
  }

  @Test
  public void testShorteningLookup() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("/aoo/bar.xml", "");
    myFixture.addFileToProject("/foo/boo.xml", "");
    myFixture.configureByText("a.java", "class A {  String s1 = \"/<caret>\"; }");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).contains("foo");
    myFixture.type("f");
    assertThat(myFixture.getLookupElementStrings()).contains("foo");
  }
}
