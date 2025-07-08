package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ClsStubBuilderTest extends LightJavaCodeInsightTestCase {
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
  
  public void testTypeAnnoOwner() {
    VirtualFile file = getClassVirtualFile("TypeAnno");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertTrue(psiFile instanceof PsiCompiledElement && psiFile instanceof PsiJavaFile);
    PsiType returnType = ((PsiJavaFile)psiFile).getClasses()[0].findMethodsByName("get1TypeParam", false)[0].getReturnType();
    PsiAnnotation topAnno = returnType.getAnnotations()[0];
    assertNotNull(topAnno);
    assertSame(returnType, topAnno.getOwner());
    assertTrue(returnType instanceof PsiClassType);
    PsiType parameter = ((PsiClassType)returnType).getParameters()[0];
    PsiAnnotation paramAnno = parameter.getAnnotations()[0];
    assertSame(parameter, paramAnno.getOwner());
  }

  public void testModifiers() { doTest("../repo/pack/" + getTestName(false)); }
  public void testModuleInfo() { doTest("module-info"); }
  public void testPackageInfo() { doTest("package-info"); }

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
    VirtualFile clsFile = getClassVirtualFile(clsPath);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static @NotNull VirtualFile getClassVirtualFile(String clsPath) {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + clsPath + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(clsFilePath);
    assertNotNull("Can't find: " + clsFilePath, clsFile);
    return clsFile;
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
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