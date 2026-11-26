// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.intention.ReplaceConstructorWithFactoryAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.ide.IdeEventQueue;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModChooseAction;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ReplaceConstructorWithFactoryTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testEmptyConstructor() { doTest("01", null); }
  @Test
  public void testSubclass() { doTest("02", null); }
  @Test
  public void testDefaultConstructor() { doTest("03", null); }
  @Test
  public void testDefaultConstructorWithTypeParams() { doTest("TypeParams", null); }
  @Test
  public void testPreserveComments() { doTest(); }
  @Test
  public void testInnerClass() { doTest("04", null); }
  @Test
  public void testNestedClass() { doTest("NestedClass", "OuterClass"); }
  @Test
  public void testNestedClass2() { doTest("NestedClass2", "InnerClass"); }
  @Test
  public void testMethodReference1() { doTest(); }
  @Test
  public void testMethodReference2() { doTest(); }
  @Test
  public void testAnonymousInheritor() { doTest("AnonymousInheritor", "Inner"); }
  @Test
  public void testAnonymousInheritor2() { doTest(); }
  @Test
  public void testSubclassVisibility() { doTest("05", null); }
  @Test
  public void testImplicitConstructorUsages() { doTest("06", null); }
  @Test
  public void testImplicitConstructorCreation() { doTest("07", null); }
  @Test
  public void testConstructorTypeParameters() { doTest("08", null); }
  @Test
  public void testInnerClass2() { doTest("InnerClass2", "SimpleClass"); }
  @Test
  public void testIncompleteClass() { assertNotAvailable(); }
  @Test
  public void testInjection() { doTest(); }
  @Test
  public void testRecords() { assertNotAvailable("RecordConstructor"); }
  @Test
  public void testImplicitClass() { assertNotAvailable(); }
  @Test
  public void testRedCode() { assertNotAvailable(); }
  @Test
  public void testImplicitClassNotAvailable() { assertNotAvailable("ImplicitClassAfterName"); }
  @Test
  public void testRedCodeFromIDEA376351() { assertNotAvailable(); }
  @Test
  public void testArrayCreation() { doTest(); }
  @Test
  public void testPrecariousAnchor() { doTest(); /* See IDEA-389677 */ }
  @Test
  public void testRawConstructorCall() { doTest(); }
  @Test
  public void testRawQualifiedInnerConstructorCall() { doTest(); }

  @Test
  public void testImplicitClassNotChoose() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", """
      private static class Neste<caret>d {
      }
      
      void main() {
          new Nested();
      }
      """);
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ModCommand command = ReadAction.computeBlocking(() -> action.perform(ActionContext.from(myFixture.getEditor(), myFixture.getFile())));
    if (!(command instanceof ModChooseAction modChooseAction)) {
      fail("must be chooser");
      return;
    }
    assertEquals(1, modChooseAction.actions().size());
  }

  @Test
  public void testWithSelection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByFile("/refactoring/replaceConstructorWithFactory/beforeWithSelection.java");
    myFixture.runInCommand(() -> {
      ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
      ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
      Presentation presentation = action.getPresentation(context);
      assertNotNull(presentation);
      ModCommand command = action.perform(context);
      ModCommandExecutor.getInstance().executeInteractively(context, command, myFixture.getEditor());
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      final LookupEx lookup = LookupManager.getActiveLookup(myFixture.getEditor());
      assertNotNull(lookup);
      LookupElement newMain = ContainerUtil.find(lookup.getItems(), l -> l.getLookupString().equals("newMain"));
      assertNotNull(newMain);
      ((LookupImpl)lookup).finishLookup('\n', newMain);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("/refactoring/replaceConstructorWithFactory/afterWithSelection.java");
    });
  }

  private void assertNotAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertNotAvailable(myFixture.getTestName(false));
  }

  private void assertNotAvailable(String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/replaceConstructorWithFactory/before" + name + ".java");
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    Presentation presentation = ReadAction.computeBlocking(() -> {
      ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
      return action.getPresentation(context);
    });
    assertNull(presentation);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(myFixture.getTestName(false), null);
  }

  private void doTest(String testName, @NonNls String targetClassName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/replaceConstructorWithFactory/before" + testName + ".java");
    myFixture.runInCommand(() -> {
      perform(targetClassName);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("/refactoring/replaceConstructorWithFactory/after" + testName + ".java");
    });
  }

  private void perform(String targetClassName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (targetClassName != null) {
      UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(targetClassName)));
    }
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
    Presentation presentation = action.getPresentation(context);
    assertNotNull(presentation);
    ModCommand command = action.perform(context);
    ModCommandExecutor modCommandExecutor = ModCommandExecutor.getInstance();
    modCommandExecutor.getPreview(command, context);
    modCommandExecutor.executeInteractively(context, command, myFixture.getEditor());
    IdeEventQueue.getInstance().flushQueue();
  }
}
