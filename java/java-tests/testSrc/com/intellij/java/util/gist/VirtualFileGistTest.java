// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.gist;

import com.intellij.openapi.application.ReadAction;
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
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.gist.PsiFileGist;
import com.intellij.util.gist.VirtualFileGist;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.util.gist.storage.GistStorageImpl.MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class VirtualFileGistTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGistGetData_ReturnsDataAsPerGistCalculator() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFileGist<String> gist = gistOfFirst3CharsFromFile();

    String file1Content = "foo bar";
    VirtualFile file1 = fileWithContent("a.txt", file1Content);
    ReadAction.runBlocking(() -> {
      assertEquals(
        gist.getFileData(myFixture.getProject(), file1),
        file1Content.substring(0, 3)
      );
    });
    String file2Content = "bar foo";
    VirtualFile file2 = fileWithContent("b.txt", file2Content);
    ReadAction.runBlocking(() -> {
      assertEquals(
        gist.getFileData(myFixture.getProject(), file2),
        file2Content.substring(0, 3)
      );
    });
  }

  @Test
  public void testGistGetData_ReturnsDataAsPerGistCalculator_EvenForHugeDataSize() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (!assumeHugeGistsAreEnabledAndSafeToTest()) {
      return;
    }

    int hugeGistSize = MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 10;

    VirtualFileGist<String> gist = gistOfFileContent();

    String hugeFile1Content = "a".repeat(hugeGistSize);
    VirtualFile file1 = fileWithContent("a.txt", hugeFile1Content);
    ReadAction.runBlocking(() -> {
      assertEquals(
        gist.getFileData(myFixture.getProject(), file1),
        hugeFile1Content
      );
    });

    String hugeFile2Content = "b".repeat(hugeGistSize);
    VirtualFile file2 = fileWithContent("b.txt", hugeFile2Content);
    ReadAction.runBlocking(() -> {
      assertEquals(
        gist.getFileData(myFixture.getProject(), file2),
        hugeFile2Content
      );
    });
  }

  @Test
  public void testGistGetData_CachesDataSuccessfully_ForTransitionsBetweenHugeAndRegularGistSize() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
        ReadAction.runBlocking(() -> {
          assertEquals(
            hugeFileContent,
            gistOfFileContent.getFileData(myFixture.getProject(), file),
            "First invocation returns (huge) file content"
          );
        });
        int invocationsBefore = invocationCounter.get();
        ReadAction.runBlocking(() -> {
          assertEquals(
            hugeFileContent,
            gistOfFileContent.getFileData(myFixture.getProject(), file),
            "Second invocation returns _same_ result"
          );
        });
        assertEquals(
          invocationsBefore,
          invocationCounter.get(),
          "...and second invocation returns _cached_ result"
        );
      }

      {//Now change file content to 'not huge':
        WriteAction.run(() -> VfsUtil.saveText(file, notHugeFileContent));
        ReadAction.runBlocking(() -> {
          assertEquals(
            notHugeFileContent,
            gistOfFileContent.getFileData(myFixture.getProject(), file),
            "First invocation returns (not huge) file content"
          );
        });
        int invocationsBefore = invocationCounter.get();
        ReadAction.runBlocking(() -> {
          assertEquals(
            notHugeFileContent,
            gistOfFileContent.getFileData(myFixture.getProject(), file),
            "Second invocation returns _same_ result"
          );
        });
        assertEquals(
          invocationsBefore,
          invocationCounter.get(),
          "...and second invocation returns _cached_ result"
        );
      }
    }
  }

  @Test
  public void testGistData_IsNotReEvaluatedOnEachCall_ButCachedPerFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();

    VirtualFile file = fileWithContent("a.txt", "foo bar");
    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(1),
        gist.getFileData(myFixture.getProject(), file),
        "Gist evaluated only once on a same unchanged file"
      );
      assertEquals(
        Integer.valueOf(1),
        gist.getFileData(myFixture.getProject(), file),
        "Gist evaluated only once on a same unchanged file"
      );
    });

    VirtualFile file2 = fileWithContent("b.txt", "");
    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(2),
        gist.getFileData(myFixture.getProject(), file2),
        "Gist re-evaluated on another file"
      );
    });
  }

  @Test
  public void testGistData_IsRecalculated_OnFileChange() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();
    VirtualFile file = fileWithContent("a.txt", "foo bar");

    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(1),
        gist.getFileData(myFixture.getProject(), file),
        "Gist evaluated 1st time on a file"
      );
    });

    WriteAction.run(() -> VfsUtil.saveText(file, "x"));
    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(2),
        gist.getFileData(myFixture.getProject(), file),
        "Gist evaluated 2nd time since file content was changed"
      );
    });

    FileContentUtilCore.reparseFiles(file);
    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(3),
        gist.getFileData(myFixture.getProject(), file),
        "Gist evaluated 3rd time since file was reparsed forcibly"
      );
    });

    // make sure the file is indexed
    ReadAction.runBlocking(() -> {
      FileBasedIndex.getInstance().processValues(IdIndex.NAME, new IdIndexEntry("foo", true), file, (_, _) -> true, GlobalSearchScope.allScope(myFixture.getProject()));
    });

    ReadAction.runBlocking(() -> {
      PushedFilePropertiesUpdater.getInstance(myFixture.getProject()).filePropertiesChanged(file, Conditions.alwaysTrue());
      assertEquals(
        Integer.valueOf(4),
        gist.getFileData(myFixture.getProject(), file),
        "Gist evaluated 4th time since file property was pushed"
      );
    });
  }

  @Test
  public void testGistData_IsNotRecalculated_OnOtherFilesChanges() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();
    VirtualFile gistFile = fileWithContent("a.txt", "foo bar");
    VirtualFile otherFile = fileWithContent("b.txt", "foo bar");

    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(1),
        gist.getFileData(myFixture.getProject(), gistFile),
        "Gist evaluated 1st time for gistFile"
      );
      assertEquals(
        Integer.valueOf(2),
        gist.getFileData(myFixture.getProject(), otherFile),
        "Gist re-evaluated 2nd time being requested for otherFile"
      );
    });

    WriteAction.run(() -> VfsUtil.saveText(otherFile, "x"));
    ReadAction.runBlocking(() -> {
      assertEquals(
        Integer.valueOf(1),
        gist.getFileData(myFixture.getProject(), gistFile),
        "Gist(gistFile) was cached, and was not re-evaluated on otherFile change"
      );
    });
  }

  @Test
  public void testGistWorksWell_WithLightVirtualFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      final VirtualFileGist<String> first3CharsGist = gistOfFirst3CharsFromFile();
      final String fileContent = "goo goo";
      final LightVirtualFile lightFile = new LightVirtualFile("a.txt", fileContent);
      assertEquals(
        fileContent.substring(0, 3),
        first3CharsGist.getFileData(myFixture.getProject(), lightFile)
      );
    });
  }

  @Test
  public void testGistCalculatesData_PerProject() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntRef invocations = new IntRef(0);
    VirtualFileGist<String> gistProjectName =
      GistManager.getInstance().newVirtualFileGist(myFixture.getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> {
        invocations.inc();
        return (p == null ? null : p.getName());
      });
    VirtualFile file = fileWithContent("a.txt", "foo bar");

    ReadAction.runBlocking(() -> {
      final Project project1 = myFixture.getProject();
      final Project project2 = ProjectManager.getInstance().getDefaultProject();

      String project1Name = project1.getName();
      String project2Name = project2.getName();

      assertEquals(
        project1Name,
        gistProjectName.getFileData(project1, file),
        "Gist calculated 1st time for project1"
      );
      assertEquals(1, invocations.get());

      assertEquals(
        project2Name,
        gistProjectName.getFileData(project2, file),
        "Gist DOES recalculated for project2"
      );
      assertEquals(2, invocations.get());

      assertEquals(
        project1Name,
        gistProjectName.getFileData(project1, file),
        "Gist does NOT recalculated for project1"
      );
      assertEquals(2, invocations.get());

      assertNull(
        gistProjectName.getFileData(null, file),
        "Gist DOES recalculated for null project"
      );
      assertEquals(3, invocations.get());
    });
  }

  @Test
  public void testGistCalculatesData_PerProject_EvenForHugeData() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (!assumeHugeGistsAreEnabledAndSafeToTest()) {
      return;
    }

    int hugeGistSize = MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 10;
    String hugeFileContent = "a".repeat(hugeGistSize);
    VirtualFile file = fileWithContent("a.txt", hugeFileContent);

    ReadAction.runBlocking(() -> {
      IntRef invocations = new IntRef(0);
      VirtualFileGist.GistCalculator<String> fileContentPlusProjectNameCalculator = (p, f) -> {
        final String content = LoadTextUtil.loadText(f).toString();
        return content + "///" + (p == null ? null : p.getName());
      };
      VirtualFileGist<String> gistFileContentPlusProjectName = GistManager.getInstance().newVirtualFileGist(
        myFixture.getTestName(true), 0,
        EnumeratorStringDescriptor.INSTANCE,
        wrapWithCounter(
          fileContentPlusProjectNameCalculator,
          invocations
        )
      );

      Project project1 = myFixture.getProject();
      Project project2 = ProjectManager.getInstance().getDefaultProject();

      assertEquals(
        fileContentPlusProjectNameCalculator.calcData(project1, file),
        gistFileContentPlusProjectName.getFileData(project1, file),
        "Gist calculated 1st time for project1"
      );
      assertEquals(1, invocations.get());

      assertEquals(
        fileContentPlusProjectNameCalculator.calcData(project2, file),
        gistFileContentPlusProjectName.getFileData(project2, file),
        "Gist calculated 1st time for project2"
      );
      assertEquals(2, invocations.get());

      assertEquals(
        fileContentPlusProjectNameCalculator.calcData(project1, file),
        gistFileContentPlusProjectName.getFileData(project1, file),
        "Gist must have same value for project1"
      );
      assertEquals(2,
                   invocations.get(), "Gist must NOT be recalculated for project1");

      assertEquals(
        fileContentPlusProjectNameCalculator.calcData(null, file),
        gistFileContentPlusProjectName.getFileData(null, file),
        "Gist MUST be recalculated for null project"
      );
      assertEquals(3,
                   invocations.get(), "Gist must NOT be recalculated for null project");
    });
  }

  @Test
  public void testAttemptToCreateSecondGist_WithTheSameName_Fails() {
    gistOfFirst3CharsFromFile();
    try {
      gistOfFirst3CharsFromFile();
      fail("Attempt to create second gist with same name must not succeed");
    }
    catch (IllegalArgumentException ignore) {
    }
  }

  @Test
  public void testGistSupportsNullData_AndStillCacheNullResultsWithoutReCalculation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntRef invocationsHolder = new IntRef(0);
    VirtualFileGist<String> gist =
      GistManager.getInstance().newVirtualFileGist(myFixture.getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> {
        invocationsHolder.inc();
        return null;
      });
    VirtualFile file = fileWithContent("a.txt", "foo bar");
    ReadAction.runBlocking(() -> {
      assertNull(gist.getFileData(myFixture.getProject(), file));
      assertNull(gist.getFileData(myFixture.getProject(), file));
      assertEquals(1, invocationsHolder.get());
    });
  }

  @Test
  public void testPsiGistUsesLastCommittedDocumentContent() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.txt", "foo bar");
    PsiFileGist<String> gist =
      GistManager.getInstance().newPsiFileGist(myFixture.getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.getText().substring(0, 3));
    ReadAction.runBlocking(() -> {
      assert "foo".equals(gist.getFileData(file));
    });

    WriteAction.run(() -> {
      VfsUtil.saveText(file.getVirtualFile(), "bar foo");
      assert file.isValid();
      assert PsiDocumentManager.getInstance(myFixture.getProject()).isUncommited(file.getViewProvider().getDocument());
      assert "foo".equals(gist.getFileData(file));
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      assert "bar".equals(gist.getFileData(file));
    });
  }

  @Test
  public void testPsiGistDoesNotLoadAST() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "package bar;");
    ReadAction.runBlocking(() -> {
      assert !file.isContentsLoaded();

      assert GistManager.getInstance()
        .newPsiFileGist(myFixture.getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.findElementAt(0).getText()).getFileData(file)
        .equals("package");
      assert !file.isContentsLoaded();
    });
  }

  @Test
  public void testPsiGistWorkForBinaryFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiFile objectClass =
        JavaPsiFacade.getInstance(myFixture.getProject()).findClass(Object.class.getName(), GlobalSearchScope.allScope(myFixture.getProject()))
          .getContainingFile();

      assert GistManager.getInstance().newPsiFileGist(myFixture.getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.getName())
        .getFileData(objectClass).equals("Object.class");
    });
  }

  @Test
  public void testPsiGistDoesNotLoadDocument() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileGist<Integer> gist = psiGistReturningCalculationsCount();
    PsiFile file = myFixture.addFileToProject("a.xtt", "foo");
    ReadAction.runBlocking(() -> {
      assert gist.getFileData(file) == 1;

      GCWatcher.tracking(PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file)).ensureCollected();
      assert PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file) == null;

      assert gist.getFileData(file) == 1;
      assert PsiDocumentManager.getInstance(myFixture.getProject()).getCachedDocument(file) == null;
    });
  }

  @Test
  public void testInvalidateDataWorksForNonPhysicalFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = ReadAction.computeBlocking(() -> PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "foo bar"));
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    VirtualFileGist<Integer> vfsGist = gistReturningCalculationsCount();
    PsiFileGist<Integer> psiGist = psiGistReturningCalculationsCount();

    ReadAction.runBlocking(() -> {
      assert 1 == vfsGist.getFileData(myFixture.getProject(), vFile);
    });
    ReadAction.runBlocking(() -> {
      assert 1 == psiGist.getFileData(psiFile) : psiGist.getFileData(psiFile);
    });

    GistManager.getInstance().invalidateData();
    ReadAction.runBlocking(() -> {
      assert 2 == vfsGist.getFileData(myFixture.getProject(), vFile);
    });
    ReadAction.runBlocking(() -> {
      assert 2 == psiGist.getFileData(psiFile);
    });
  }

  @Test
  public void testDataIsRecalculatedWhenAncestorDirectoryChanges() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFileGist<Integer> gist = gistReturningCalculationsCount();
    VirtualFile file = fileWithContent("foo/bar/a.txt", "");
    ReadAction.runBlocking(() -> {
      assert 1 == gist.getFileData(myFixture.getProject(), file);
    });
    WriteAction.run(() -> file.getParent().getParent().rename(this, "goo"));
    ReadAction.runBlocking(() -> {
      assert 2 == gist.getFileData(myFixture.getProject(), file);
    });
  }

  @Test
  public void testReindexCountDoesNotChangeOnReparse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int reindexCount = ((GistManagerImpl)GistManager.getInstance()).getReindexCount();

    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "package bar;");

    FileContentUtilCore.reparseFiles(file.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

    assertEquals(reindexCount, ((GistManagerImpl)GistManager.getInstance()).getReindexCount(), "Reindex count must not change");
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String gistId = myFixture.getTestName(true) + ".gistOfFirst" + maxSize + "CharsFromFile";
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String gistId = myFixture.getTestName(true) + ".calculationsCount";
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntRef invocationsHolder = new IntRef(0);
    String gistId = myFixture.getTestName(true) + ".psiGistReturningCalculationsCount";
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.addFileToProject(fileName, fileContent).getVirtualFile();
  }

  @AfterEach
  void tearDown() {
    ((GistManagerImpl)GistManager.getInstance()).resetReindexCount();
  }
}
