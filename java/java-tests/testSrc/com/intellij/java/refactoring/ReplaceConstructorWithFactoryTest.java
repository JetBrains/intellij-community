// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.ReplaceConstructorWithFactoryAction;
import com.intellij.ide.IdeEventQueue;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.Presentation;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class ReplaceConstructorWithFactoryTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyConstructor() { runTest("01", null); }

  public void testSubclass() { runTest("02", null); }

  public void testDefaultConstructor() { runTest("03", null); }
  public void testDefaultConstructorWithTypeParams() { runTest("TypeParams", null); }

  public void testInnerClass() { runTest("04", null); }
  
  public void testNestedClass() { runTest("NestedClass", "OuterClass"); }
  
  public void testNestedClass2() { runTest("NestedClass2", "InnerClass"); }

  public void testSubclassVisibility() { runTest("05", null); }

  public void testImplicitConstructorUsages() { runTest("06", null); }

  public void testImplicitConstructorCreation() { runTest("07", null); }

  public void testConstructorTypeParameters() { runTest("08", null); }
  
  public void testInnerClass2() { runTest("InnerClass2", "SimpleClass"); }

  private void runTest(final String testIndex, @NonNls String targetClassName) {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + testIndex + ".java");
    perform(targetClassName);
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
    ModCommandExecutor.getInstance().executeInteractively(context, command, getEditor());
    IdeEventQueue.getInstance().flushQueue();
  }
}
