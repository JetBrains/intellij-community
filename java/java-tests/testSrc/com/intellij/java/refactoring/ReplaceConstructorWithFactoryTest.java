// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.ReplaceConstructorWithFactoryAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.ide.IdeEventQueue;
import com.intellij.modcommand.*;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED;

public class ReplaceConstructorWithFactoryTest extends LightRefactoringTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyConstructor() { runTest("01", null); }

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

  public void testSubclass() { runTest("02", null); }

  public void testDefaultConstructor() { runTest("03", null); }

  public void testDefaultConstructorWithTypeParams() { runTest("TypeParams", null); }

  public void testPreserveComments() { runTest(getTestName(false), null); }

  public void testInnerClass() { runTest("04", null); }

  public void testNestedClass() { runTest("NestedClass", "OuterClass"); }

  public void testNestedClass2() { runTest("NestedClass2", "InnerClass"); }

  public void testAnonymousInheritor() { runTest("AnonymousInheritor", "Inner"); }

  public void testAnonymousInheritor2() { runTest("AnonymousInheritor2", null); }

  public void testSubclassVisibility() { runTest("05", null); }

  public void testImplicitConstructorUsages() { runTest("06", null); }

  public void testImplicitConstructorCreation() { runTest("07", null); }

  public void testConstructorTypeParameters() { runTest("08", null); }

  public void testInnerClass2() { runTest("InnerClass2", "SimpleClass"); }

  public void testIncompleteClass() {
    assertNotAvailable("IncompleteClass");
  }

  public void testInjection() {
    runTest("Injection", null);
  }

  public void testRecords() {
    assertNotAvailable("RecordConstructor");
  }

  public void testImplicitClass() {
    assertNotAvailable("ImplicitClass");
  }

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

  public void testRedCode() {
    assertNotAvailable("RedCode");
  }

  public void testRedCodeFromIDEA376351() {
    assertNotAvailable("RedCodeFromIDEA376351");
  }

  private void assertNotAvailable(String name) {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + name + ".java");
    ReplaceConstructorWithFactoryAction action = new ReplaceConstructorWithFactoryAction();
    ActionContext context = ActionContext.from(getEditor(), getFile());
    Presentation presentation = action.getPresentation(context);
    assertNull(presentation);
  }

  private void runTest(final String testIndex, @NonNls String targetClassName) {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + testIndex + ".java");
    setupEditorForInjectedLanguage();
    perform(targetClassName);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile("/refactoring/replaceConstructorWithFactory/after" + testIndex + ".java");
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
