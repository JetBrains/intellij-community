/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.LightIdeaTestCase;

import java.io.File;

/**
 * @author max
 */
public class ClsBuilderTest extends LightIdeaTestCase {
  public void testUtilList() { doTest("java/util/List.class"); }
  public void testNullable() { doTest("org/jetbrains/annotations/Nullable.class"); }
  public void testUtilCollections() { doTest("java/util/Collections.class"); }
  public void testUtilHashMap() { doTest("java/util/HashMap.class"); }
  public void testUtilMap() { doTest("java/util/Map.class"); }
  public void testTimeUnit() { doTest("java/util/concurrent/TimeUnit.class"); }

  public void testTestSuite() { doTest(); }
  public void testDoubleTest() { doTest(); /* IDEA-53195 */ }
  public void testAnnotatedNonStaticInnerClassConstructor() { doTest(); }
  public void testAnnotatedEnumConstructor() { doTest(); }

  public void testModifiers() {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/repo/pack/" + getTestName(false) + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assertNotNull(clsFile);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTest() {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + getTestName(false) + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assertNotNull(clsFile);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTest(String className) {
    VirtualFile clsFile = findFile(className);
    assertNotNull(clsFile);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static void doTest(VirtualFile clsFile, String resultFileName) {
    try {
      PsiFileStub stub = ClsFileImpl.buildFileStub(clsFile, clsFile.contentsToByteArray());
      assertNotNull(stub);
      String actual = ((StubBase)stub).printTree();

      File resultFile = new File(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + resultFileName);
      if (!resultFile.exists()) {
        System.out.println("No expected data found at: " + resultFile + ", creating one.");
        FileUtil.writeToFile(resultFile, actual);
        fail("No test data found. Created one");
      }

      String expected = StringUtil.convertLineSeparators(FileUtil.loadFile(resultFile));

      assertEquals(expected, actual);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private VirtualFile findFile(final String className) {
    final VirtualFile[] roots = getProjectJDK().getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      VirtualFile vFile = root.findFileByRelativePath(className);
      if (vFile != null) return vFile;
    }

    fail("Cannot file class file for: " + className);
    return null;
  }
}