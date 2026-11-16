// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryTestUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.junit.jupiter.api.Assertions.fail;

/// Variant of [GenerateJavadocTest] to handle Markdown comment generation
/// Since the workflow for Markdown comments is different ([com.intellij.codeInsight.completion.CompletionContributor]-based),
/// it is a separate class
@TestApplication
public class GenerateJavadocMarkdownTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generateJavadocMarkdown/");

  @Test
  public void testClass() { doTestWithTemplates(); }
  @Test
  public void testClassParam() { doTestWithTemplates(); }
  @Test
  public void testMethodEmpty() { doTestWithTemplates(); }
  @Test
  public void testMethodParam() { doTestWithTemplates(); }
  @Test
  public void testMethodReturn() { doTestWithTemplates(); }
  @Test
  public void testMethodThrows() { doTestWithTemplates(); }
  @Test
  public void testMethodFull() { doTestWithTemplates(); }
  @Test
  public void testMethodAbstract() { doTestWithTemplates(); }

  /* Tests with this name are expecting to explicitly have no generation available */
  @Test
  public void testGenerationDisabled01() { doTestWithTemplates(); }
  @Test
  public void testGenerationDisabled02() { doTestWithTemplates(); }
  @Test
  public void testGenerationDisabled03() { doTestWithTemplates(); }

  @Test
  public void testDanglingDoc() { doTestWithTemplates(); }
  @Test
  public void testNoClassCastException() { doTestWithTemplates(); }

  /// Sanity check: verify template-less code path doesn't explode
  @Test
  public void testMethodFullNoTemplate() { doTestNoTemplates(); }

  private void doTestNoTemplates() {
    RegistryTestUtil.withValue(Registry.get("java.javadoc.use.templates"), Boolean.FALSE, ()-> {
      doTest();
      return Unit.INSTANCE;
    });
  }

  private void doTestWithTemplates() {
    RegistryTestUtil.withValue(Registry.get("java.javadoc.use.templates"), Boolean.TRUE, ()-> {
      doTest();
      return Unit.INSTANCE;
    });
  }

  private void doTest() {
    LightCompletionFixture completion = this.completion.get();
    String name = completion.getTestName(true);
    String beforeFile = name + ".before.java";
    String afterFile = name + ".after.java";

    EdtTestUtil.runInEdtAndWait(() -> {
      completion.configureByFile(beforeFile);
      performAction();

      // For tests that do not have changes, fallback on the before file
      File after = new File(completion.getFixture().getTestDataPath(), afterFile);
      completion.checkResultByFile(after.exists() ? afterFile : beforeFile);
    });
  }

  private void performAction() {
    LightCompletionFixture completion = this.completion.get();
    completion.getFixture().completeBasic();
    LookupElement[] items = completion.getItems();
    if (items.length == 1) {
      completion.getFixture().type("\t");
      return;
    }

    // Either no action, or there are way too many actions available
    String targetItem = RefactorJBundle.message("insert.javadoc.template");
    boolean actionFound = ContainerUtil.exists(items, element-> {
      return element.getLookupString().equals(targetItem);
    });
    boolean noActionExpected = completion.getTestName(false).contains("GenerationDisabled");
    if (actionFound) {
      if (noActionExpected) {
        fail("The action was found but was expected to not be present");
      }
      fail("The action should not be available when there are multiple choices: " + Arrays.toString(items));
    }
  }
}
