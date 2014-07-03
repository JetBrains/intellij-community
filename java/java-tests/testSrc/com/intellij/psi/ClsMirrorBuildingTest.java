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
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.compiled.InnerClassSourceStrategy;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.org.objectweb.asm.ClassReader;

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
  public void testGrEnum() { doTest(); }

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
    String txtPath = testDir + "/psi/cls/mirror/Class.txt";
    doTest(clsPath, txtPath);
  }

  public void testStrayInnersFiltering() throws IOException {
    String path = JavaTestUtil.getJavaTestDataPath() + "/../../mockJDK-1.8/jre/lib/rt.jar!/java/lang/Class.class";
    VirtualFile file = StandardFileSystems.jar().findFileByPath(path);
    assertNotNull(path, file);

    InnerClassSourceStrategy<VirtualFile> strategy = new InnerClassSourceStrategy<VirtualFile>() {
      @Override
      public VirtualFile findInnerClass(String innerName, VirtualFile outerClass) {
        String baseName = outerClass.getNameWithoutExtension();
        VirtualFile child = outerClass.getParent().findChild(baseName + "$" + innerName + ".class");
        // stray inner classes should be filtered out
        assert child != null : innerName + " is not an inner class of " + outerClass;
        return child;
      }

      @Override
      public void accept(VirtualFile innerClass, StubBuildingVisitor<VirtualFile> visitor) {
        try {
          byte[] bytes = innerClass.contentsToByteArray();
          new ClassReader(bytes).accept(visitor, ClassReader.SKIP_FRAMES);
        }
        catch (IOException ignored) { }
      }
    };
    PsiJavaFileStubImpl stub = new PsiJavaFileStubImpl("do.not.know.yet", true);
    StubBuildingVisitor<VirtualFile> visitor = new StubBuildingVisitor<VirtualFile>(file, strategy, stub, 0, null);
    new ClassReader(file.contentsToByteArray()).accept(visitor, ClassReader.SKIP_FRAMES);
  }

  private void doTest() {
    doTest(getTestName(false));
  }

  private static void doTest(String name) {
    String testDir = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/mirror/";
    doTest(testDir + "pkg/" + name + ".class", testDir + name + ".txt");
  }

  private static void doTest(String clsPath, String txtPath) {
    VirtualFileSystem fs = clsPath.contains("!/") ? StandardFileSystems.jar() : StandardFileSystems.local();
    VirtualFile file = fs.findFileByPath(clsPath);
    assertNotNull(clsPath, file);

    String expected;
    try {
      expected = StringUtil.trimTrailing(PlatformTestUtil.loadFileText(txtPath));
    }
    catch (IOException e) {
      fail(e.getMessage());
      return;
    }

    assertEquals(expected, ClsFileImpl.decompile(file).toString());
  }
}
