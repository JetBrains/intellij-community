/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.IOException;

public class ClsMirrorBuildingTest extends LightIdeaTestCase {
  public void testSimpleEnum() { doTest(); }
  public void testEnumWithFields() { doTest(); }
  public void testNormalClass() { doTest(); }
  public void testNested() { doTest(); }
  public void testDeprecated() { doTest(); }
  public void testAnnotations() { doTest(); }
  public void testParameterNames() { doTest(); }
  public void testEmptyEnum() { doTest(); }
  public void test$BuckClass() { doTest(); }
  public void testBuckClass$() { doTest(); }
  public void testExtMethods() { doTest(); }
  public void testMethodReceiver() { doTest(); }
  public void testPackageInfo() { doTest("package-info"); }
  public void testEA40568() { doTest(); }
  public void testPrimitives() { doTest(); }
  public void testClassRefs() { doTest(); }
  public void testEA46236() { doTest("ValuedEnum"); }
  public void testKotlinFunList() { doTest(); }
  public void testMiddle$Buck() { doTest(); }
  public void testDefaultPackage() { doTest(); }
  public void testLocalClass() { doTest(); }
  public void testBounds() { doTest(); }

  public void testTextPsiMismatch() {
    CommonCodeStyleSettings.IndentOptions options =
      CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().getIndentOptions(JavaFileType.INSTANCE);
    int indent = options.INDENT_SIZE;
    options.INDENT_SIZE *= 2;
    try {
      doTest("Bounds");
    }
    finally {
      options.INDENT_SIZE = indent;
    }
  }

  public void testJdk8Class() {
    String testDir = JavaTestUtil.getJavaTestDataPath();
    String clsPath = testDir + "/../../mockJDK-1.8/jre/lib/rt.jar!/java/lang/Class.class";
    String txtPath = testDir + "/psi/cls/mirror/" + "Class.txt";
    doTest(clsPath, txtPath);
  }

  private void doTest() {
    doTest(getTestName(false));
  }

  private static void doTest(String name) {
    String testDir = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/mirror/";
    doTest(testDir + "pkg/" + name + ".class", testDir + name + ".txt");
  }

  private static void doTest(String clsPath, String txtPath) {
    VirtualFileSystem fs = clsPath.contains("!/") ? JarFileSystem.getInstance() : LocalFileSystem.getInstance();
    VirtualFile vFile = fs.findFileByPath(clsPath);
    assertNotNull(clsPath, vFile);
    PsiFile clsFile = getPsiManager().findFile(vFile);
    assertNotNull(vFile.getPath(), clsFile);

    String expected;
    try {
      expected = StringUtil.trimTrailing(PlatformTestUtil.loadFileText(txtPath));
    }
    catch (IOException e) {
      fail(e.getMessage());
      return;
    }

    assertEquals(expected, ((ClsFileImpl)clsFile).getMirror().getText());
  }
}
