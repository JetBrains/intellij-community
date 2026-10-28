// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
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
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED;

public class ReplaceConstructorWithFactoryTest extends LightJavaCodeInsightTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyConstructor() { doTest("01", null); }
  public void testSubclass() { doTest("02", null); }
  public void testDefaultConstructor() { doTest("03", null); }
  public void testDefaultConstructorWithTypeParams() { doTest("TypeParams", null); }
  public void testPreserveComments() { doTest(); }
  public void testInnerClass() { doTest("04", null); }
  public void testNestedClass() { doTest("NestedClass", "OuterClass"); }
  public void testNestedClass2() { doTest("NestedClass2", "InnerClass"); }
  public void testMethodReference1() { doTest(); }
  public void testMethodReference2() { doTest(); }
  public void testAnonymousInheritor() { doTest("AnonymousInheritor", "Inner"); }
  public void testAnonymousInheritor2() { doTest(); }
  public void testSubclassVisibility() { doTest("05", null); }
  public void testImplicitConstructorUsages() { doTest("06", null); }
  public void testImplicitConstructorCreation() { doTest("07", null); }
  public void testConstructorTypeParameters() { doTest("08", null); }
  public void testInnerClass2() { doTest("InnerClass2", "SimpleClass"); }
  public void testIncompleteClass() { assertNotAvailable(); }
  public void testInjection() { doTest(); }
  public void testRecords() { assertNotAvailable("RecordConstructor"); }
  public void testImplicitClass() { assertNotAvailable(); }
  public void testRedCode() { assertNotAvailable(); }
  public void testImplicitClassNotAvailable() { assertNotAvailable("ImplicitClassAfterName"); }
  public void testRedCodeFromIDEA376351() { assertNotAvailable(); }
  public void testArrayCreation() { doTest(); }
  public void testPrecariousAnchor() { doTest(); /* See IDEA-389677 */ }
  public void testRawConstructorCall() { doTest(); }
  public void testRawQualifiedInnerConstructorCall() { doTest(); }

  public void testImplicitClassNotChoose() {
    configureFromFileText("A.java", """
      private static class Neste<caret>d {
      }
      
      void main() {
          new Nested();
      }
      """);
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ModCommand command = action.perform(ActionContext.from(getEditor(), getFile()));
    if (!(command instanceof ModChooseAction modChooseAction)) {
      fail("must be chooser");
      return;
    }
    assertSize(1, modChooseAction.actions());
  }

  public void testWithSelection() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    configureByFile("/refactoring/replaceConstructorWithFactory/beforeWithSelection.java");
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ActionContext context = ActionContext.from(getEditor(), getFile());
    Presentation presentation = action.getPresentation(context);
    assertNotNull(presentation);
    ModCommand command = action.perform(context);
    ModCommandExecutor.getInstance().executeInteractively(context, command, getEditor());
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    final LookupEx lookup = LookupManager.getActiveLookup(getEditor());
    assertNotNull(lookup);
    LookupElement newMain = ContainerUtil.find(lookup.getItems(), l -> l.getLookupString().equals("newMain"));
    assertNotNull(newMain);
    ((LookupImpl)lookup).finishLookup('\n', newMain);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile("/refactoring/replaceConstructorWithFactory/afterWithSelection.java");
  }

  private void assertNotAvailable() {
    assertNotAvailable(getTestName(false));
  }

  private void assertNotAvailable(String name) {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + name + ".java");
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ActionContext context = ActionContext.from(getEditor(), getFile());
    Presentation presentation = action.getPresentation(context);
    assertNull(presentation);
  }

  private void doTest() {
    doTest(getTestName(false), null);
  }

  private void doTest(String testName, @NonNls String targetClassName) {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + testName + ".java");
    setupEditorForInjectedLanguage();
    perform(targetClassName);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile("/refactoring/replaceConstructorWithFactory/after" + testName + ".java");
  }

  private void perform(String targetClassName) {
    if (targetClassName != null) {
      UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(targetClassName)));
    }
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ActionContext context = ActionContext.from(getEditor(), getFile());
    Presentation presentation = action.getPresentation(context);
    assertNotNull(presentation);
    ModCommand command = action.perform(context);
    ModCommandExecutor modCommandExecutor = ModCommandExecutor.getInstance();
    modCommandExecutor.getPreview(command, context);
    modCommandExecutor.executeInteractively(context, command, getEditor());
    IdeEventQueue.getInstance().flushQueue();
  }
}
