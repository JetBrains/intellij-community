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
package com.intellij.unscramble;

import com.intellij.JavaTestUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import javax.swing.*;
import java.io.File;

/**
 * @author Dmitry Avdeev
 *         Date: 4/9/12
 */
public class UnscrambleDialogTest extends JavaCodeInsightFixtureTestCase {
  private RunContentDescriptor myContent;

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myContent);
    }
    finally {
      myContent = null;
      super.tearDown();
    }
  }

  public void testStacktrace() {
    showText("");
    Icon icon = myContent.getIcon();
    String name = myContent.getDisplayName();
    assertEquals(null, icon);
    assertEquals("<Stacktrace>", name);
  }

  public void testException() {
    showText("java.lang.NullPointerException\n" +
             "\tat com.intellij.psi.css.resolve.impl.XhtmlFileInfo.findOneStyleSheet(XhtmlFileInfo.java:291)\n" +
             "\tat com.intellij.psi.css.resolve.impl.XhtmlFileInfo.getStylesheets(XhtmlFileInfo.java:174)\n" +
             "\tat com.intellij.psi.css.resolve.impl.XhtmlFileInfo.initStylesheets(XhtmlFileInfo.java:119)");
    assertIcon("exception.png", myContent.getIcon());
    assertEquals("NPE", myContent.getDisplayName());
  }

  public void testThreadDump() throws Exception {
    File file = new File(getTestDataPath() + "threaddump.txt");
    String s = FileUtil.loadFile(file);
    showText(s);
    assertIcon("threaddump.png", myContent.getIcon());
    assertEquals("<Threads>", myContent.getDisplayName());
  }

  public void testDeadlock() throws Exception {
    File file = new File(getTestDataPath() + "deadlock.txt");
    String s = FileUtil.loadFile(file);
    showText(s);
    assertIcon("killProcess.png", myContent.getIcon());
    assertEquals("<Deadlock>", myContent.getDisplayName());
  }

  private void showText(String unscramble) {
    RunContentDescriptor descriptor = UnscrambleDialog.showUnscrambledText(null, "foo", null, getProject(), unscramble);
    assertNotNull(descriptor);
    Disposer.register(myFixture.getTestRootDisposable(), descriptor);
    myContent = descriptor;
  }

  private static void assertIcon(String s, Icon icon) {
    assertTrue(icon.toString().contains(s));
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/unscramble/";
  }
}
