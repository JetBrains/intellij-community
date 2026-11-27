package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ClsStubBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17()));

  @TempDir
  private Path tempDir;
  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testUtilList() { doClassTest("java.util.List"); }
  @Test
  public void testNullable() { doClassTest("org.jetbrains.annotations.Nullable"); }
  @Test
  public void testUtilCollections() { doClassTest("java.util.Collections"); }
  @Test
  public void testUtilHashMap() { doClassTest("java.util.HashMap"); }
  @Test
  public void testUtilMap() { doClassTest("java.util.Map"); }
  @Test
  public void testTimeUnit() { doClassTest("java.util.concurrent.TimeUnit"); }

  @Test
  public void testMethodParameters() { doSourceTest("Parameters", "Parameters", "-parameters"); }
  @Test
  public void testLocalVariableTable() { doSourceTest("Parameters", "ParametersDebug", "-g"); }
  @Test
  public void testMethodParamsAndLocalVarTable() { doSourceTest("Parameters", "Parameters", "-g", "-parameters"); }
  @Test
  public void testNoParameterNames() { doSourceTest("Parameters", "ParametersGenerated"); }
  @Test
  public void testGroovyStuff() { doSourceTest("GroovyStuff", "GroovyStuff"); }

  @Test
  public void testTestSuite() { doTest(); }
  @Test
  public void testDoubleTest() { doTest(); /* IDEA-53195 */ }
  @Test
  public void testTypeAnno() { doTest(); }
  
  @Test
  public void testTypeAnnoOwner() {
    VirtualFile file = getClassVirtualFile("TypeAnno");
    Project project = projectFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      assertTrue(psiFile instanceof PsiCompiledElement && psiFile instanceof PsiJavaFile);
      PsiType returnType = ((PsiJavaFile)psiFile).getClasses()[0].findMethodsByName("get1TypeParam", false)[0].getReturnType();
      PsiAnnotation topAnno = returnType.getAnnotations()[0];
      assertNotNull(topAnno);
      assertSame(returnType, topAnno.getOwner());
      assertTrue(returnType instanceof PsiClassType);
      PsiType parameter = ((PsiClassType)returnType).getParameters()[0];
      PsiAnnotation paramAnno = parameter.getAnnotations()[0];
      assertSame(parameter, paramAnno.getOwner());
    });
  }

  @Test
  public void testModifiers() { doTest("../repo/pack/" + testName); }
  @Test
  public void testModuleInfo() { doTest("module-info"); }
  @Test
  public void testPackageInfo() { doTest("package-info"); }

  private void doClassTest(String className) {
    JavaPsiFacadeEx facade = JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject());
    VirtualFile clsFile = ReadAction.computeBlocking(() -> {
      PsiClass aClass = facade.findClass(className);
      assertNotNull(aClass, "Cannot find class: " + className);
      return aClass.getContainingFile().getVirtualFile();
    });
    doTest(clsFile, testName + ".txt");
  }

  private void doSourceTest(String srcFileName, String resultFileName, String... options) {
    File out = IoTestUtil.createTestDir(tempDir.toFile(), "out");
    try {
      File srcFile = IdeaTestUtil.findSourceFile(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + srcFileName);
      IdeaTestUtil.compileFile(srcFile, out, options);
      String clsFilePath = out.getPath() + '/' + srcFileName + ".class";
      VirtualFile clsFile = StandardFileSystems.local().refreshAndFindFileByPath(clsFilePath);
      assertNotNull(clsFile, "Can't find: " + clsFilePath);
      doTest(clsFile, resultFileName + ".txt");
    }
    finally {
      FileUtil.delete(out);
    }
  }

  private void doTest() {
    doTest(testName);
  }

  private void doTest(String clsPath) {
    VirtualFile clsFile = getClassVirtualFile(clsPath);
    doTest(clsFile, testName + ".txt");
  }

  private static @NotNull VirtualFile getClassVirtualFile(String clsPath) {
    String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + clsPath + ".class";
    VirtualFile clsFile = StandardFileSystems.local().refreshAndFindFileByPath(clsFilePath);
    assertNotNull(clsFile, "Can't find: " + clsFilePath);
    return clsFile;
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

      PlatformTestUtil.assertSameLinesWithFile(resultFile.getPath(), actual, true);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}