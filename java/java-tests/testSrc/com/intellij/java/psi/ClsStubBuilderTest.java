// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightIdeaTestCase;

import java.io.File;

public class ClsStubBuilderTest extends LightIdeaTestCase {
  public void testUtilList() { doClassTest("java.util.List"); }
  public void testNullable() { doClassTest("org.jetbrains.annotations.Nullable"); }
  public void testUtilCollections() { doClassTest("java.util.Collections"); }
  public void testUtilHashMap() { doClassTest("java.util.HashMap"); }
  public void testUtilMap() { doClassTest("java.util.Map"); }
  public void testTimeUnit() { doClassTest("java.util.concurrent.TimeUnit"); }

  public void testMethodParameters() { doSourceTest("Parameters", "Parameters", "-parameters"); }
  public void testLocalVariableTable() { doSourceTest("Parameters", "ParametersDebug", "-g"); }
  public void testMethodParamsAndLocalVarTable() { doSourceTest("Parameters", "Parameters", "-g", "-parameters"); }
  public void testNoParameterNames() { doSourceTest("Parameters", "ParametersGenerated"); }
  public void testGroovyStuff() { doSourceTest("GroovyStuff", "GroovyStuff"); }

  public void testTestSuite() { doTest(); }
  public void testDoubleTest() { doTest(); /* IDEA-53195 */ }
  public void testTypeAnno() { doTest(); }

  public void testModifiers() { doTest("../repo/pack/" + getTestName(false)); }
  public void testModuleInfo() { doTest("module-info"); }

  private void doClassTest(String className) {
    PsiClass aClass = getJavaFacade().findClass(className);
    assertNotNull("Cannot find class: " + className, aClass);
    VirtualFile clsFile = aClass.getContainingFile().getVirtualFile();
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static void doSourceTest(String srcFileName, String resultFileName, String... options) {
    File out = IoTestUtil.createTestDir(new File(FileUtil.getTempDirectory()), "out");
    try {
      File srcFile = IdeaTestUtil.findSourceFile(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + srcFileName);
      IdeaTestUtil.compileFile(srcFile, out, options);
      String clsFilePath = out.getPath() + '/' + srcFileName + ".class";
      VirtualFile clsFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(clsFilePath);
      assertNotNull("Can't find: " + clsFilePath, clsFile);
      doTest(clsFile, resultFileName + ".txt");
    }
    finally {
      FileUtil.delete(out);
    }
  }

  private void doTest() {
    doTest(getTestName(false));
  }

  private void doTest(String clsPath) {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + clsPath + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(clsFilePath);
    assertNotNull("Can't find: " + clsFilePath, clsFile);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static void doTest(VirtualFile clsFile, String resultFileName) {
    try {
      PsiFileStub<?> stub = ClsFileImpl.buildFileStub(clsFile, clsFile.contentsToByteArray());
      assertNotNull(stub);
      String actual = ((StubBase<?>)stub).printTree().trim();

      File resultFile = new File(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + resultFileName);
      if (!resultFile.exists()) {
        FileUtil.writeToFile(resultFile, actual);
        fail("No test data found at: " + resultFile + ", creating one");
      }

      assertSameLinesWithFile(resultFile.getPath(), actual, true);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}