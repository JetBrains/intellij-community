// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ClassFileViewProvider;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.compiled.InnerClassSourceStrategy;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ClsMirrorBuildingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());

  @TempDir
  private Path tempDir;
  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testSimpleEnum() { doTest(); }
  @Test
  public void testEnumWithFields() { doTest(); }
  @Test
  public void testNormalClass() { doTest(); }
  @Test
  public void testNested() { doTest(); }
  @Test
  public void testDeprecated() { doTest(); }
  @Test
  public void testAnnotations() { doTest(); }
  @Test
  public void testMoreAnnotations() { doTest(); }
  @Test
  public void testMemberAnnotations() { doTest(); }
  @Test
  public void testParameterNames() { doTest(); }
  @Test
  public void testEmptyEnum() { doTest(); }
  @Test
  public void test$BuckClass() { doTest(); }
  @Test
  public void testBuckClass$() { doTest(); }
  @Test
  public void testExtMethods() { doTest(); }
  @Test
  public void testMethodReceiver() { doTest(); }
  @Test
  public void testPackageInfo() { doTest("package-info"); }
  @Test
  public void testEA40568() { doTest(); }
  @Test
  public void testPrimitives() { doTest(); }
  @Test
  public void testClassRefs() { doTest(); }
  @Test
  public void testEA46236() { doTest("ValuedEnum"); }
  @Test
  public void testKotlinFunList() { doTest(); }
  @Test
  public void testMiddle$Buck() { doTest(); }
  @Test
  public void testDefaultPackage() { doTest(); }
  @Test
  public void testLocalClass() { doTest(); }
  @Test
  public void testBounds() { doTest(); }
  @Test
  public void testGroovy() { doTest(); }
  @Test
  public void testGroovyBaseObject() { doTest(); }
  @Test
  public void testGrEnum() { doTest(); }
  @Test
  public void testGrTrait() { doTest(); }
  @Test
  public void testSuspiciousParameterNames() { doTest(); }
  @Test
  public void testTimeUnit() { doTest(); }
  @Test
  public void testTypeAnnotations() { doTest(); }
  @Test
  public void testTypeAnno() { doTest(); }
  @Test
  public void testTypeAnnoNestedGeneric() { doTest(); }
  @Test
  public void testExtendsObjectAnnotated() { doTest(); }
  @Test
  public void testRecordTest() { doTest(); }
  @Test
  public void testRecordTestCustomHash() { doTest(); }
  @Test
  public void testInheritFromDollar() { doTest(); }
  @Test
  public void testInheritFromDollar$1() { doTest(); }
  @Test
  public void testSealed() { doTest(); }
  @Test
  public void testCompanyDO() { doTest(); }
  @Test
  public void testGenericRecord() { doTest(); }
  @Test
  public void testMoreInterfaces() { doTest(); }
  @Test
  public void testFewerInterfaces() { doTest(); }
  @Test
  public void testCompanyDOInDumbMode() {
    testDumbMode("CompanyDO");
  }

  @Test
  public void testFieldWithSimilarAnnotation() {
    testDumbMode("FieldWithSimilarAnnotation");
  }

  private static void testDumbMode(String testName) {
    String testDir = getTestDataDir();
    String clsPath = getClsPath(testName, testDir);
    String txtPath = getTxtPath(testName, testDir);
    VirtualFile file = (clsPath.contains("!/") ? StandardFileSystems.jar() : StandardFileSystems.local()).refreshAndFindFileByPath(clsPath);
    assertNotNull(file, clsPath);

    Project project = projectFixture.get().getProject();
    DumbModeTestUtils.runInDumbModeSynchronously(project, () -> ReadAction.runBlocking(() -> {
      BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(()->{
        PlatformTestUtil.assertSameLinesWithFile(txtPath, ClsFileImpl.decompile(file).toString());
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiCompiledElement compiledElement) {
          PsiElement mirror = compiledElement.getMirror();
          assertNotNull(mirror);
        }
        return null;
      });
    }));
  }

  @Test
  public void testTextPsiMismatch() {
    // JUnit 3 gave each light test temporary code style settings, so the decompiler did not see this change.
    Project project = projectFixture.get().getProject();
    CodeStyle.setTemporarySettings(project, CodeStyle.createTestSettings());
    try {
      CommonCodeStyleSettings.IndentOptions options = CodeStyle.getSettings(project).getIndentOptions(JavaFileType.INSTANCE);
      int indent = options.INDENT_SIZE;
      options.INDENT_SIZE = 10;
      try {
        doTest("Bounds");
      }
      finally {
        options.INDENT_SIZE = indent;
      }
    }
    finally {
      CodeStyle.dropTemporarySettings(project);
    }
  }

  @Test
  public void testJdk8Class() {
    String testDir = JavaTestUtil.getJavaTestDataPath();
    doTest(testDir + "/../../mockJDK-1.8/jre/lib/rt.jar!/java/lang/Class.class", testDir + "/psi/cls/mirror/Class.txt");
  }

  @Test
  public void testStaticMethodInInterface() {
    String testDir = JavaTestUtil.getJavaTestDataPath();
    doTest(testDir + "/../../mockJDK-1.8/jre/lib/rt.jar!/java/util/function/Function.class", testDir + "/psi/cls/mirror/Function.txt");
  }

  @Test
  public void testStrayInnersFiltering() throws IOException {
    String path = JavaTestUtil.getJavaTestDataPath() + "/../../mockJDK-1.8/jre/lib/rt.jar!/java/lang/Class.class";
    VirtualFile file = StandardFileSystems.jar().findFileByPath(path);
    assertNotNull(file, path);

    InnerClassSourceStrategy<VirtualFile> strategy = new InnerClassSourceStrategy<>() {
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
        catch (IOException ignored) {
        }
      }
    };
    PsiJavaFileStubImpl stub = new PsiJavaFileStubImpl(true);
    StubBuildingVisitor<VirtualFile> visitor = new StubBuildingVisitor<>(file, strategy, stub, 0, null);
    new ClassReader(file.contentsToByteArray()).accept(visitor, ClassReader.SKIP_FRAMES);
  }

  @Test
  public void testDocumentReuse() throws IOException {
    File classFile = new File(tempDir.toFile(), "ReuseTest.class");
    Files.write(classFile.toPath(), new byte[0]);
    VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(classFile.getPath());
    assertNotNull(vFile, classFile.getPath());
    Project project = projectFixture.get().getProject();
    PsiFile psiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findFile(vFile));
    assertNotNull(psiFile);
    String testDir = getTestDataDir();

    Files.copy(new File(testDir, "pkg/ReuseTestV1.class").toPath(), classFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    vFile.refresh(false, false);
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
    ReadAction.runBlocking(() -> {
      String text1 = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> psiFile.getText());
      assertTrue(text1.contains("private int f1"), text1);
      assertFalse(text1.contains("private int f2"), text1);
      Document doc1 = FileDocumentManager.getInstance().getCachedDocument(vFile);
      assertNotNull(doc1);
      assertSame(doc1, PsiDocumentManager.getInstance(project).getDocument(psiFile));
    });

    Files.copy(new File(testDir, "pkg/ReuseTestV2.class").toPath(), classFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    vFile.refresh(false, false);
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
    ReadAction.runBlocking(() -> {
      String text2 = psiFile.getText();
      assertTrue(text2.contains("private int f1"), text2);
      assertTrue(text2.contains("private int f2"), text2);
      Document doc2 = FileDocumentManager.getInstance().getCachedDocument(vFile);
      assertNotNull(doc2);
      assertSame(doc2, PsiDocumentManager.getInstance(project).getDocument(psiFile));
    });
  }

  @Test
  public void testElementAt() {
    String path = getTestDataDir() + "/pkg/SimpleEnum.class";
    VirtualFile vFile = StandardFileSystems.local().findFileByPath(path);
    assertNotNull(vFile, path);
    ReadAction.runBlocking(() -> {
      PsiFile psiFile = PsiManager.getInstance(projectFixture.get().getProject()).findFile(vFile);
      assertNotNull(psiFile, path);
      int length = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> psiFile.getTextLength());
      for (int i = 0; i < length; i++) {
        PsiElement element = psiFile.findElementAt(i);
        assertTrue(element != null && !(element instanceof ClsElementImpl), i + ":" + element);
      }
    });
  }

  @Test
  public void testMirrorParsingIsCancellable() {
    String path = JavaTestUtil.getJavaTestDataPath() + "/../../mockJDK-1.8/jre/lib/rt.jar!/java/lang/Class.class";
    VirtualFile vFile = StandardFileSystems.jar().refreshAndFindFileByPath(path);
    assertNotNull(vFile, path);
    ReadAction.runBlocking(() -> {
      ClsFileImpl clsFile = assertInstanceOf(ClsFileImpl.class, PsiManager.getInstance(projectFixture.get().getProject()).findFile(vFile));

      boolean[] canceledInsideParsing = {false};
      ProgressIndicatorBase indicator = new ProgressIndicatorBase();
      CoreProgressManager.CheckCanceledHook hook = __ -> {
        //try to match inside decompiler
        if (!indicator.isCanceled() && StackWalker.getInstance().walk(
          frames -> frames.anyMatch(f -> "ensureParsed".equals(f.getMethodName()) &&
                                         "com.intellij.psi.impl.source.tree.LazyParseableElement".equals(f.getClassName())))) {
          canceledInsideParsing[0] = true;
          indicator.cancel();
          return true;
        }
        return false;
      };

      ProgressManagerImpl progressManager = (ProgressManagerImpl)ProgressManager.getInstance();
      try {
        progressManager.runWithHook(hook, () -> BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> {
          progressManager.runProcess(() -> clsFile.getMirror(), indicator);
          return null;
        }));
        fail("mirror parsing wasn't canceled; could it be running inside a non-cancelable section?");
      }
      catch (ProcessCanceledException e) {
        assertTrue(canceledInsideParsing[0]);
      }

      assertNull(clsFile.getCachedMirror());

      PsiElement mirror = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> clsFile.getMirror());
      assertTrue(assertInstanceOf(PsiJavaFile.class, mirror).getClasses().length > 0);
    });
  }

  @Test
  public void testInnerClassDetection() throws IOException {
    assertTrue(isInner("pkg/Nested$Inner1"));
    assertTrue(isInner("pkg/Nested$Inner1$Inner2"));
    assertTrue(isInner("pkg/NormalClass$1"));
    assertTrue(isInner("pkg/LocalClass$1MyRunnable"));
    assertTrue(isInner("pkg/KindaInner$RealInner$"));
    assertTrue(isInner("pkg/KindaInner$Real$Inner"));
    assertTrue(isInner("pkg/Groovy$Inner"));
    assertTrue(isInner("pkg/Groovy$_closure1"));
    assertTrue(isInner("weird/ToStringStyle$1"));

    assertFalse(isInner("pkg/KindaInner$Class"));
    assertFalse(isInner("weird/ToStringStyle"));
  }

  @Test
  public void testModuleInfo() {
    String testDir = getTestDataDir();
    doTest(testDir + "../stubBuilder/module-info.class", testDir + "module-info.txt");
  }

  @Test
  public void testCopyClassFileDoesNotDecompile() throws Exception {
    String testDir = getTestDataDir();
    File srcClass = new File(testDir, "pkg/SimpleEnum.class");
    Path destDir = Files.createDirectories(new File(tempDir.toFile(), "copyClsTest_" + System.nanoTime()).toPath());
    try {
      Path destClass = destDir.resolve("SimpleEnum.class");
      Files.copy(srcClass.toPath(), destClass);

      VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(destClass.toString());
      assertNotNull(vFile, destClass.toString());
      VirtualFile parentDir = vFile.getParent();
      assertNotNull(parentDir);

      Project project = projectFixture.get().getProject();
      PsiFile psiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findFile(vFile));
      assertNotNull(psiFile);

      // Ensure no document is cached for the compiled file.
      assertNull(FileDocumentManager.getInstance().getCachedDocument(vFile));

      // Copy the .class file using PsiDirectory.copyFileFrom.
      com.intellij.psi.PsiDirectory psiDir = ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findDirectory(parentDir));
      assertNotNull(psiDir);
      WriteAction.runAndWait(() -> {
        psiDir.copyFileFrom("SimpleEnumCopy.class", psiFile);
      });

      // Verify the copy was created.
      VirtualFile copiedVFile = parentDir.findChild("SimpleEnumCopy.class");
      assertNotNull(copiedVFile, "Copied .class file should exist");

      // Verify that no document was created for either file.
      // If getCachedDocument is used (instead of getDocument), no decompilation occurs.
      assertNull(FileDocumentManager.getInstance().getCachedDocument(vFile),
        "Original file must not have a cached document");
      assertNull(FileDocumentManager.getInstance().getCachedDocument(copiedVFile),
        "Copied file must not have a cached document");
    }
    finally {
      NioFiles.deleteRecursively(destDir);
    }
  }

  private static String getTestDataDir() {
    return JavaTestUtil.getJavaTestDataPath() + "/psi/cls/mirror/";
  }

  private void doTest() {
    doTest(testName);
  }

  private static void doTest(String name) {
    String testDir = getTestDataDir();
    doTest(getClsPath(name, testDir), getTxtPath(name, testDir));
  }

  private static @NotNull String getTxtPath(String name, String testDir) {
    return testDir + name + ".txt";
  }

  private static @NotNull String getClsPath(String name, String testDir) {
    return testDir + "pkg/" + name + ".class";
  }

  private static void doTest(String clsPath, String txtPath) {
    VirtualFile file = (clsPath.contains("!/") ? StandardFileSystems.jar() : StandardFileSystems.local()).refreshAndFindFileByPath(clsPath);
    assertNotNull(file, clsPath);

    PlatformTestUtil.assertSameLinesWithFile(txtPath, ClsFileImpl.decompile(file).toString());
  }

  private static boolean isInner(String name) {
    VirtualFile file = StandardFileSystems.local().findFileByPath(getTestDataDir() + name + ".class");
    assertNotNull(file);
    return ClassFileViewProvider.isInnerClass(file);
  }
}