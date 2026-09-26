// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;

import java.util.List;

/**
 * Test that "run class" is calculated correctly and is available in Dumb Mode.
 */
public class ApplicationRunClassDumbModeTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNormalMain() {
    doTest("""
             public class Main {
               public static void <caret>main(String[] args) {}
             }
             """, "Main", "Main");
  }

  public void testNestedMain() {
    doTest("""
             class Main {
               static class Nested {
                 public static void <caret>main(String[] args) {}
               }
             }
             """, "Main.Nested", "Main$Nested");
  }


  private void doTest(@Language("JAVA") String code, String mainClassName, String runClassName) {
    myFixture.configureByText("Main.java", code);

    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.getFirst();
    ActionGroup group = mark.getPopupMenuActions();
    assertNotNull(group);
    PresentationFactory factory = new PresentationFactory();
    List<AnAction> list = ContainerUtil.findAll(Utils.expandActionGroup(
      group, factory, DataContext.EMPTY_CONTEXT, ActionPlaces.UNKNOWN, ActionUiKind.NONE), action -> {
      String text = factory.getPresentation(action).getText();
      return text != null && text.startsWith("Run '") && text.endsWith("'");
    });
    assertEquals(list.toString(), 1, list.size());

    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      myFixture.testAction(list.getFirst());
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });

    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(getProject()).getSelectedConfiguration();
    assertNotNull(selectedConfiguration);
    RunConfiguration configuration = selectedConfiguration.getConfiguration();
    assertInstanceOf(configuration, ApplicationConfiguration.class);

    ApplicationConfiguration applicationConfiguration = (ApplicationConfiguration)configuration;

    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      assertEquals(mainClassName, applicationConfiguration.getMainClassName());
      assertEquals(mainClassName, applicationConfiguration.getMainClass().getQualifiedName());
      assertEquals(runClassName, applicationConfiguration.getRunClass());
    });
  }
}
