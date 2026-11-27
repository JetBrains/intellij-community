// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.unscramble;

import com.intellij.JavaTestUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.CoreIconManager;
import com.intellij.unscramble.UnscrambleDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.io.File;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class UnscrambleDialogTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/unscramble/");

  private RunContentDescriptor myContent;

  @BeforeEach
  void setUpIcons() {
    // The icon loader must not use a fake empty icon.
    IconManager.Companion.activate(new CoreIconManager());
  }

  @AfterEach
  void tearDown() {
    try {
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        if (myContent != null) {
          Disposer.dispose(myContent);
        }
      });
    }
    finally {
      myContent = null;
      IconManager.Companion.deactivate();
      IconLoader.INSTANCE.clearCacheInTests();
    }
  }

  @Test
  public void testStacktrace() {
    showText("");
    Icon icon = myContent.getIcon();
    String name = myContent.getDisplayName();
    assertEquals(null, icon);
    assertEquals("<Stacktrace>", name);
  }

  @Test
  public void testException() {
    showText("""
               java.lang.NullPointerException
               \tat com.intellij.psi.css.resolve.impl.XhtmlFileInfo.findOneStyleSheet(XhtmlFileInfo.java:291)
               \tat com.intellij.psi.css.resolve.impl.XhtmlFileInfo.getStylesheets(XhtmlFileInfo.java:174)
               \tat com.intellij.psi.css.resolve.impl.XhtmlFileInfo.initStylesheets(XhtmlFileInfo.java:119)""");
    assertIcon("lightning.svg", myContent.getIcon());
    assertEquals("NPE", myContent.getDisplayName());
  }

  @Test
  public void testThreadDump() throws Exception {
    File file = new File(codeInsightFixture.get().getTestDataPath() + "threaddump.txt");
    String s = FileUtil.loadFile(file);
    showText(s);
    assertIcon("dump.svg", myContent.getIcon());
    assertEquals("<Threads>", myContent.getDisplayName());
  }

  @Test
  public void testJcmdJsonNumericIdentifiersThreadDump() {
    showText("""
               {
                 "threadDump": {
                   "formatVersion": 2,
                   "processId": 79302,
                   "threadContainers": [
                     {
                       "container": "<root>",
                       "threads": [
                         {
                           "tid": 157,
                           "name": "main",
                           "state": "RUNNABLE",
                           "stack": [
                             "example.Main.run(Main.java:1)"
                           ]
                         },
                         {
                           "tid": 158,
                           "name": "worker",
                           "state": "WAITING",
                           "stack": [
                             "example.Worker.await(Worker.java:1)"
                           ]
                         }
                       ],
                       "threadCount": 2
                     }
                   ]
                 }
               }""");
    assertIcon("dump.svg", myContent.getIcon());
    assertEquals("<Threads>", myContent.getDisplayName());
  }

  @Test
  public void testDeadlock() throws Exception {
    File file = new File(codeInsightFixture.get().getTestDataPath() + "deadlock.txt");
    String s = FileUtil.loadFile(file);
    showText(s);
    assertIcon("killProcess.svg", myContent.getIcon());
    assertEquals("<Deadlock>", myContent.getDisplayName());
  }

  private void showText(String unscramble) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    RunContentDescriptor descriptor =
      EdtTestUtil.runInEdtAndGet(() -> UnscrambleDialog.showUnscrambledText(null, "foo", null, myFixture.getProject(), unscramble));
    assertNotNull(descriptor);
    Disposer.register(myFixture.getTestRootDisposable(), descriptor);
    myContent = descriptor;
  }

  private static void assertIcon(String s, Icon icon) {
    assertThat(icon.toString()).contains(s);
  }
}
