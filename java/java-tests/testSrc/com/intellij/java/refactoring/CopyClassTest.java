// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeArgumentsProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaIndexingModeCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestApplication
public class CopyClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/copyClass/");
  private IndexingMode myMode;
  private JavaCodeInsightTestFixture myFixture;

  /**
   * Runs {@code body} in {@code mode} on the EDT, as the JUnit 3 suite of the indexing modes did.
   */
  private void runInMode(IndexingMode mode, ThrowableRunnable<Exception> body) throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      LightJavaCodeInsightTestFixture fixture = multiFile.get().getFixture();
      myMode = mode;
      myFixture = JavaIndexingModeCodeInsightTestFixture.Companion.wrapFixture(fixture, mode);
      IndexingMode.ShutdownToken token = mode.setUpTest(fixture.getProject(), fixture.getTestRootDisposable());
      try {
        body.run();
      }
      finally {
        mode.tearDownTest(fixture.getProject(), token);
      }
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testReplaceAllOccurrences(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doTest("Foo", "Bar"));
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testPrivateMethodsInInterfaces(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doTest("Foo", "Bar"));
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testReplaceAllOccurrences1(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doTest("Foo", "Bar"));
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testRecursiveTypes(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doTest("Foo", "Bar"));
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testConflictInSameFolder(IndexingMode mode) throws Exception {
    runInMode(mode, () -> {
      assertThatThrownBy(() -> doTest("Foo", "Foo")).isInstanceOf(RuntimeException.class).hasMessageContaining("already exist");
    });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testLibraryClass(IndexingMode mode) throws Exception {  // IDEADEV-28791
    runInMode(mode, () -> {
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
      doTest("java.util.ArrayList", "Bar");
    });
  }

  private void doTest(final String oldName, final String copyName) throws Exception {
    myFixture.copyDirectoryToProject(multiFile.get().getTestName(true), "");
    performAction(oldName, copyName);

    VirtualFile fileAfter = myFixture.findFileInTempDir(copyName + ".java");
    VirtualFile fileExpected = myFixture.findFileInTempDir(copyName + ".expected.java");

    PlatformTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  private void performAction(final String oldName, final String copyName) throws Exception {
    final PsiClass oldClass = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(() -> myFixture.findClass(oldName));

    WriteCommandAction.writeCommandAction(myFixture.getProject()).run(
                                             () -> {
                                               PsiDirectory targetDirectory =
                                                 PsiManager.getInstance(myFixture.getProject()).findDirectory(myFixture.getTempDirFixture().findOrCreateDir(""));
                                               Map<PsiFile, PsiClass[]> sourceClasses =
                                                 Collections.singletonMap(oldClass.getNavigationElement().getContainingFile(),
                                                                          new PsiClass[]{oldClass});
                                               CopyClassesHandler.doCopyClasses(sourceClasses, copyName, targetDirectory, myFixture.getProject());
                                               PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
                                             });
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testPackageLocalClasses(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doMultifileTest());
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testPackageLocalMethods(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doMultifileTest());
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testPackageLocalAndExtends(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doMultifileTest());
  }

  //copy all classes from p1 -> p2
  private void doMultifileTest() {
    multiFile.get().doTest(() -> {
      // The JUnit 3 fixture of the mode did this after it copied the directory.
      myMode.ensureIndexingStatus(myFixture.getProject());
      final HashMap<PsiFile, PsiClass[]> map = new HashMap<>();
      final VirtualFile sourceDir = myFixture.findFileInTempDir("p1");
      for (VirtualFile file : sourceDir.getChildren()) {
        final PsiFile psiFile = PsiManager.getInstance(myFixture.getProject()).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          map.put(psiFile, ((PsiJavaFile)psiFile).getClasses());
        }
      }

      final VirtualFile targetVDir = myFixture.findFileInTempDir("p2");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        CopyClassesHandler.doCopyClasses(map, null, PsiManager.getInstance(myFixture.getProject()).findDirectory(targetVDir), myFixture.getProject());
      });
    }, "multifile/" + multiFile.get().getTestName(true));
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testPackageHierarchy(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doPackageCopy());
  }

  @ParameterizedTest(name = "[{0}]")
  @ArgumentsSource(IndexingModeArgumentsProvider.class)
  public void testPackageOneLevelHierarchy(IndexingMode mode) throws Exception {
    runInMode(mode, () -> doPackageCopy());
  }

  private void doPackageCopy() {
    multiFile.get().doTest(() -> {
      // The JUnit 3 fixture of the mode did this after it copied the directory.
      myMode.ensureIndexingStatus(myFixture.getProject());
      final PsiDirectory sourceP1Dir = PsiManager.getInstance(myFixture.getProject()).findDirectory(myFixture.findFileInTempDir("p1"));
      final PsiDirectory targetP2Dir = PsiManager.getInstance(myFixture.getProject()).findDirectory(myFixture.findFileInTempDir("p2"));
      new CopyClassesHandler().doCopy(new PsiElement[]{sourceP1Dir}, targetP2Dir);
    }, "multifile/" + multiFile.get().getTestName(true));
  }
}
