/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.LightIdeaTestCase;

import java.io.File;

/**
 * @author max
 */
public class ClsStubBuilderTest extends LightIdeaTestCase {
  public void testUtilList() { doClassTest("java.util.List"); }
  public void testNullable() { doClassTest("org.jetbrains.annotations.Nullable"); }
  public void testUtilCollections() { doClassTest("java.util.Collections"); }
  public void testUtilHashMap() { doClassTest("java.util.HashMap"); }
  public void testUtilMap() { doClassTest("java.util.Map"); }
  public void testTimeUnit() { doClassTest("java.util.concurrent.TimeUnit"); }

  public void testTestSuite() { doTest(); }
  public void testDoubleTest() { doTest(); /* IDEA-53195 */ }
  public void testAnnotatedNonStaticInnerClassConstructor() { doTest(); }
  public void testAnnotatedEnumConstructor() { doTest(); }
  public void testInterfaceMethodParameters() { doTest(); }
  public void testEnumMethodParameters() { doTest(); }

  public void testModifiers() { doTest("../repo/pack/" + getTestName(false)); }
  public void testModuleInfo() { doTest("module-info"); }

  private void doClassTest(String className) {
    PsiClass aClass = getJavaFacade().findClass(className);
    assertNotNull("Cannot find class: " + className, aClass);
    VirtualFile clsFile = aClass.getContainingFile().getVirtualFile();
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTest() {
    doTest(getTestName(false));
  }

  private void doTest(String clsPath) {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + clsPath + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assertNotNull("Can't find: " + clsFilePath, clsFile);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static void doTest(VirtualFile clsFile, String resultFileName) {
    try {
      PsiFileStub stub = ClsFileImpl.buildFileStub(clsFile, clsFile.contentsToByteArray());
      assertNotNull(stub);
      String actual = ((StubBase)stub).printTree().trim();

      File resultFile = new File(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + resultFileName);
      if (!resultFile.exists()) {
        System.out.println("No expected data found at: " + resultFile + ", creating one.");
        FileUtil.writeToFile(resultFile, actual);
        fail("No test data found. Created one");
      }

      String expected = StringUtil.convertLineSeparators(FileUtil.loadFile(resultFile)).trim();

      assertEquals(expected, actual);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}