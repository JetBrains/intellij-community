// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.;
package com.intellij.util.gist;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.util.gist.storage.GistStorageImpl.MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES;

public class VirtualFileGistTest extends LightJavaCodeInsightFixtureTestCase {

  public void testGistGetData_ReturnsDataAsPerGistCalculator() {
    VirtualFileGist<String> gist = gistOfFirst3CharsFromFile();

    String file1Content = "foo bar";
    assertEquals(
      gist.getFileData(getProject(), fileWithContent("a.txt", file1Content)),
      file1Content.substring(0, 3)
    );
    String file2Content = "bar foo";
    assertEquals(
      gist.getFileData(getProject(), fileWithContent("b.txt", file2Content)),
      file2Content.substring(0, 3)
    );
  }

  public void testGistGetData_ReturnsDataAsPerGistCalculator_EvenForHugeDataSize() {
    if (!assumeHugeGistsAreEnabledAndSafeToTest()) {
      return;
    }

    int hugeGistSize = MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 10;

    VirtualFileGist<String> gist = gistOfFileContent();


    String hugeFile1Content = "a".repeat(hugeGistSize);
    VirtualFile file1 = fileWithContent("a.txt", hugeFile1Content);
    assertEquals(
      gist.getFileData(getProject(), file1),
      hugeFile1Content
    );

    String hugeFile2Content = "b".repeat(hugeGistSize);
    VirtualFile file2 = fileWithContent("b.txt", hugeFile2Content);
    assertEquals(
      gist.getFileData(getProject(), file2),
      hugeFile2Content
    );
  }

  public void testGistGetData_CachesDataSuccessfully_ForTransitionsBetweenHugeAndRegularGistSize() throws IOException {
    if (!assumeHugeGistsAreEnabledAndSafeToTest()) {
      return;
    }

    int hugeGistSize = MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 10;
    int notHugeGistSize = MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES / 10;

    IntRef invocationCounter = new IntRef();
    VirtualFileGist<String> gistOfFileContent = gistOfFileContent(invocationCounter);

    String hugeFileContent = "a".repeat(hugeGistSize);
    String notHugeFileContent = "b".repeat(notHugeGistSize);
    VirtualFile file = fileWithContent("a.txt", hugeFileContent);

    final int enoughTries = 8;
    for (int i = 0; i < enoughTries; i++) {
      {//Put 'huge' content into a file:
        WriteAction.run(() -> VfsUtil.saveText(file, hugeFileContent));
        assertEquals(
          "First invocation returns (huge) file content",
          hugeFileContent,
          gistOfFileContent.getFileData(getProject(), file)
        );
        int invocationsBefore = invocationCounter.get();
        assertEquals(
          "Second invocation returns _same_ result",
          hugeFileContent,
          gistOfFileContent.getFileData(getProject(), file)
        );
        assertEquals(
          "...and second invocation returns _cached_ result",
          invocationsBefore,
          invocationCounter.get()
        );
      }

      {//Now change file content to 'not huge':
        WriteAction.run(() -> VfsUtil.saveText(file, notHugeFileContent));
        assertEquals(
          "First invocation returns (not huge) file content",
          notHugeFileContent,
          gistOfFileContent.getFileData(getProject(), file)
        );
        int invocationsBefore = invocationCounter.get();
        assertEquals(
          "Second invocation returns _same_ result",
          notHugeFileContent,
          gistOfFileContent.getFileData(getProject(), file)
        );
        assertEquals(
          "...and second invocation returns _cached_ result",
          invocationsBefore,
          invocationCounter.get()
        );
      }
    }
  }

  public void testGistData_IsNotReEvaluatedOnEachCall_ButCachedPerFile() {
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();

    VirtualFile file = fileWithContent("a.txt", "foo bar");
    assertEquals(
      "Gist evaluated only once on a same unchanged file",
      Integer.valueOf(1),
      gist.getFileData(getProject(), file)
    );
    assertEquals(
      "Gist evaluated only once on a same unchanged file",
      Integer.valueOf(1),
      gist.getFileData(getProject(), file)
    );

    assertEquals(
      "Gist re-evaluated on another file",
      Integer.valueOf(2),
      gist.getFileData(getProject(), fileWithContent("b.txt", ""))
    );
  }

  public void testGistData_IsRecalculated_OnFileChange() throws IOException {
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();
    VirtualFile file = fileWithContent("a.txt", "foo bar");

    assertEquals(
      "Gist evaluated 1st time on a file",
      Integer.valueOf(1),
      gist.getFileData(getProject(), file)
    );

    WriteAction.run(() -> VfsUtil.saveText(file, "x"));
    assertEquals(
      "Gist evaluated 2nd time since file content was changed",
      Integer.valueOf(2),
      gist.getFileData(getProject(), file)
    );

    FileContentUtilCore.reparseFiles(file);
    assertEquals(
      "Gist evaluated 3rd time since file was reparsed forcibly",
      Integer.valueOf(3),
      gist.getFileData(getProject(), file)
    );

    PushedFilePropertiesUpdater.getInstance(getProject()).filePropertiesChanged(file, Conditions.alwaysTrue());
    assertEquals(
      "Gist evaluated 4th time since file property was pushed",
      Integer.valueOf(4),
      gist.getFileData(getProject(), file)
    );
  }

  public void testGistData_IsNotRecalculated_OnOtherFilesChanges() throws IOException {
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();
    VirtualFile gistFile = fileWithContent("a.txt", "foo bar");
    VirtualFile otherFile = fileWithContent("b.txt", "foo bar");

    assertEquals(
      "Gist evaluated 1st time for gistFile",
      Integer.valueOf(1),
      gist.getFileData(getProject(), gistFile)
    );
    assertEquals(
      "Gist re-evaluated 2nd time being requested for otherFile",
      Integer.valueOf(2),
      gist.getFileData(getProject(), otherFile)
    );

    WriteAction.run(() -> VfsUtil.saveText(otherFile, "x"));
    assertEquals(
      "Gist(gistFile) was cached, and was not re-evaluated on otherFile change",
      Integer.valueOf(1),
      gist.getFileData(getProject(), gistFile)
    );
  }

  public void testGistWorksWell_WithLightVirtualFiles() {
    final VirtualFileGist<String> first3CharsGist = gistOfFirst3CharsFromFile();
    final String fileContent = "goo goo";
    final LightVirtualFile lightFile = new LightVirtualFile("a.txt", fileContent);
    assertEquals(
      fileContent.substring(0, 3),
      first3CharsGist.getFileData(getProject(), lightFile)
    );
  }

  public void testGistCalculatesData_PerProject() {
    IntRef invocations = new IntRef(0);
    VirtualFileGist<String> gistProjectName =
      GistManager.getInstance().newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> {
        invocations.inc();
        return (p == null ? null : p.getName());
      });
    VirtualFile file = fileWithContent("a.txt", "foo bar");

    final Project project1 = getProject();
    final Project project2 = ProjectManager.getInstance().getDefaultProject();

    String project1Name = project1.getName();
    String project2Name = project2.getName();

    assertEquals(
      "Gist calculated 1st time for project1",
      project1Name,
      gistProjectName.getFileData(project1, file)
    );
    assertEquals(1, invocations.get());

    assertEquals(
      "Gist DOES recalculated for project2",
      project2Name,
      gistProjectName.getFileData(project2, file)
    );
    assertEquals(2, invocations.get());

    assertEquals(
      "Gist does NOT recalculated for project1",
      project1Name,
      gistProjectName.getFileData(project1, file)
    );
    assertEquals(2, invocations.get());

    assertNull(
      "Gist DOES recalculated for null project",
      gistProjectName.getFileData(null, file)
    );
    assertEquals(3, invocations.get());
  }

  public void testGistCalculatesData_PerProject_EvenForHugeData() {
    if (!assumeHugeGistsAreEnabledAndSafeToTest()) {
      return;
    }

    int hugeGistSize = MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 10;
    String hugeFileContent = "a".repeat(hugeGistSize);
    VirtualFile file = fileWithContent("a.txt", hugeFileContent);

    IntRef invocations = new IntRef(0);
    VirtualFileGist.GistCalculator<String> fileContentPlusProjectNameCalculator = (p, f) -> {
      final String content = LoadTextUtil.loadText(f).toString();
      return content + "///" + (p == null ? null : p.getName());
    };
    VirtualFileGist<String> gistFileContentPlusProjectName = GistManager.getInstance().newVirtualFileGist(
      getTestName(true), 0,
      EnumeratorStringDescriptor.INSTANCE,
      wrapWithCounter(
        fileContentPlusProjectNameCalculator,
        invocations
      )
    );

    Project project1 = getProject();
    Project project2 = ProjectManager.getInstance().getDefaultProject();

    assertEquals(
      "Gist calculated 1st time for project1",
      fileContentPlusProjectNameCalculator.calcData(project1, file),
      gistFileContentPlusProjectName.getFileData(project1, file)
    );
    assertEquals(1, invocations.get());

    assertEquals(
      "Gist calculated 1st time for project2",
      fileContentPlusProjectNameCalculator.calcData(project2, file),
      gistFileContentPlusProjectName.getFileData(project2, file)
    );
    assertEquals(2, invocations.get());

    assertEquals(
      "Gist must have same value for project1",
      fileContentPlusProjectNameCalculator.calcData(project1, file),
      gistFileContentPlusProjectName.getFileData(project1, file)
    );
    assertEquals("Gist must NOT be recalculated for project1",
                 2, invocations.get());

    assertEquals(
      "Gist MUST be recalculated for null project",
      fileContentPlusProjectNameCalculator.calcData(null, file),
      gistFileContentPlusProjectName.getFileData(null, file)
    );
    assertEquals("Gist must NOT be recalculated for null project",
                 3, invocations.get());
  }

  public void testAttemptToCreateSecondGist_WithTheSameName_Fails() {
    gistOfFirst3CharsFromFile();
    try {
      gistOfFirst3CharsFromFile();
      fail("Attempt to create second gist with same name must not succeed");
    }
    catch (IllegalArgumentException ignore) {
    }
  }

  public void testGistSupportsNullData_AndStillCacheNullResultsWithoutReCalculation() {
    IntRef invocationsHolder = new IntRef(0);
    VirtualFileGist<String> gist =
      GistManager.getInstance().newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> {
        invocationsHolder.inc();
        return null;
      });
    VirtualFile file = fileWithContent("a.txt", "foo bar");
    assertNull(gist.getFileData(getProject(), file));
    assertNull(gist.getFileData(getProject(), file));
    assertEquals(1, invocationsHolder.get());
  }

  public void testPsiGistUsesLastCommittedDocumentContent() throws IOException {
    PsiFile file = myFixture.addFileToProject("a.txt", "foo bar");
    PsiFileGist<String> gist =
      GistManager.getInstance().newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.getText().substring(0, 3));
    assert "foo".equals(gist.getFileData(file));

    WriteAction.run(() -> {
      VfsUtil.saveText(file.getVirtualFile(), "bar foo");
      assert file.isValid();
      assert PsiDocumentManager.getInstance(getProject()).isUncommited(file.getViewProvider().getDocument());
      assert "foo".equals(gist.getFileData(file));
    });

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assert "bar".equals(gist.getFileData(file));
  }

  public void testPsiGistDoesNotLoadAST() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "package bar;");
    assert !file.isContentsLoaded();

    assert GistManager.getInstance()
      .newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.findElementAt(0).getText()).getFileData(file)
      .equals("package");
    assert !file.isContentsLoaded();
  }

  public void testPsiGistWorkForBinaryFiles() {
    PsiFile objectClass =
      JavaPsiFacade.getInstance(getProject()).findClass(Object.class.getName(), GlobalSearchScope.allScope(getProject()))
        .getContainingFile();

    assert GistManager.getInstance().newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.getName())
      .getFileData(objectClass).equals("Object.class");
  }

  public void testPsiGistDoesNotLoadDocument() {
    PsiFileGist<Integer> gist = psiGistReturningCalculationsCount();
    PsiFile file = myFixture.addFileToProject("a.xtt", "foo");
    assert gist.getFileData(file) == 1;

    GCWatcher.tracking(PsiDocumentManager.getInstance(getProject()).getCachedDocument(file)).ensureCollected();
    assert PsiDocumentManager.getInstance(getProject()).getCachedDocument(file) == null;

    assert gist.getFileData(file) == 1;
    assert PsiDocumentManager.getInstance(getProject()).getCachedDocument(file) == null;
  }

  public void testInvalidateDataWorksForNonPhysicalFiles() {
    PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "foo bar");
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    VirtualFileGist<Integer> vfsGist = gistReturningCalculationsCount();
    PsiFileGist<Integer> psiGist = psiGistReturningCalculationsCount();

    assert 1 == vfsGist.getFileData(getProject(), vFile);
    assert 1 == psiGist.getFileData(psiFile) : psiGist.getFileData(psiFile);

    GistManager.getInstance().invalidateData();
    assert 2 == vfsGist.getFileData(getProject(), vFile);
    assert 2 == psiGist.getFileData(psiFile);
  }

  public void testDataIsRecalculatedWhenAncestorDirectoryChanges() throws IOException {
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();
    VirtualFile file = fileWithContent("foo/bar/a.txt", "");
    assert 1 == gist.getFileData(getProject(), file);
    WriteAction.run(() -> file.getParent().getParent().rename(this, "goo"));
    assert 2 == gist.getFileData(getProject(), file);
  }

  public void testReindexCountDoesNotChangeOnReparse() {
    int reindexCount = ((GistManagerImpl)GistManager.getInstance()).getReindexCount();

    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "package bar;");

    FileContentUtilCore.reparseFiles(file.getVirtualFile());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertEquals("Reindex count must not change", reindexCount, ((GistManagerImpl)GistManager.getInstance()).getReindexCount());
  }

  /* ========================= test infrastructure: ================================================== */

  private static boolean assumeHugeGistsAreEnabledAndSafeToTest() {
    //Normally, we should use Assume.assumeTrue(...) in the branches below.
    // But junit3 doesn't support AssumptionFailedException yet, hence we're
    // forced to do early-return in a test method
    if (MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES <= 0) {
      //"Huge Gists saving to dedicated files is disabled -- skip the test"
      return false;
    }
    if (MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES >= 50 * IOUtil.MiB) {
      //"Max Gists size is too big to test (" + MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + ", risk of OoM) -- skip the test"
      return false;
    }
    return true;
  }

  /** Gist returns first 3 letters from the file */
  private @NotNull VirtualFileGist<String> gistOfFirst3CharsFromFile() {
    return gistOfFirstNCharsFromFile(3, null);
  }

  private @NotNull VirtualFileGist<String> gistOfFileContent() {
    return gistOfFileContent(null);
  }

  private @NotNull VirtualFileGist<String> gistOfFileContent(@Nullable IntRef invocationCounter) {
    return gistOfFirstNCharsFromFile(Integer.MAX_VALUE, invocationCounter);
  }


  private @NotNull VirtualFileGist<String> gistOfFirstNCharsFromFile(int maxSize,
                                                                     @Nullable IntRef invocationCounter) {
    String gistId = getTestName(true) + ".gistOfFirst" + maxSize + "CharsFromFile";
    int gistVersion = 0;
    VirtualFileGist.GistCalculator<String> calculator = (p, f) -> {
      final String content = LoadTextUtil.loadText(f).toString();
      return content.substring(0, Math.min(maxSize, content.length()));
    };
    return GistManager.getInstance().newVirtualFileGist(
      gistId,
      gistVersion,
      EnumeratorStringDescriptor.INSTANCE,
      invocationCounter == null ? calculator : wrapWithCounter(calculator, invocationCounter)
    );
  }

  private static <V> VirtualFileGist.GistCalculator<V> wrapWithCounter(@NotNull VirtualFileGist.GistCalculator<V> calculator,
                                                                       @NotNull IntRef invocationCounter) {
    return (project, file) -> {
      invocationCounter.inc();
      return calculator.calcData(project, file);
    };
  }

  /** Gist calculator counts its invocations and return the number (starting from 1 on a first call) */
  private VirtualFileGist<Integer> gistReturningCalculationsCount() {
    final String gistId = getTestName(true) + ".calculationsCount";
    IntRef invocationsHolder = new IntRef(0);
    return GistManager.getInstance().newVirtualFileGist(
      gistId,
      0,
      EnumeratorIntegerDescriptor.INSTANCE,
      (p, f) -> {
        invocationsHolder.inc();
        return invocationsHolder.get();
      }
    );
  }

  private PsiFileGist<Integer> psiGistReturningCalculationsCount() {
    IntRef invocationsHolder = new IntRef(0);
    String gistId = getTestName(true) + ".psiGistReturningCalculationsCount";
    return GistManager.getInstance().newPsiFileGist(
      gistId,
      /*version: */0,
      EnumeratorIntegerDescriptor.INSTANCE,
      p -> {
        invocationsHolder.inc();
        return invocationsHolder.get();
      }
    );
  }


  private VirtualFile fileWithContent(@NotNull String fileName,
                                      @NotNull String fileContent) {
    return myFixture.addFileToProject(fileName, fileContent).getVirtualFile();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((GistManagerImpl)GistManager.getInstance()).resetReindexCount();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
