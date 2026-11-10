// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.indexing;

import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileTypeIndexTest;
import com.intellij.util.indexing.IndexingStamp;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class FileBasedIndexTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSurviveOnFileTypeChange() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "class Foo { String bar; }");
    myFixture.testHighlighting();
    EdtTestUtil.runInEdtAndWait(() -> {
      Disposable disposable = Disposer.newDisposable();
      FileType foo = FileTypeIndexTest.registerFakeFileType(myFixture.getTestName(false), "test", disposable);
      String fooName = foo.getName();
      Disposer.dispose(disposable);
      assertNull(FileTypeManager.getInstance().findFileTypeByName(fooName));
    });
    myFixture.configureByText("Bar.java", "class Bar { String bar; }");
    myFixture.testHighlighting();
  }

  @Test
  public void testLargeFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String largeFileText = "class Foo { String bar; }" + StringUtil.repeat(" ", FileSizeLimit.getDefaultContentLoadLimit() + 42);
    VirtualFile file = myFixture.addFileToProject("Foo.java", largeFileText).getVirtualFile();
    ReadAction.runBlocking(() -> {
      int fileId = ((VirtualFileWithId)file).getId();

      assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(StubUpdatingIndex.INDEX_ID));
      assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(TrigramIndex.INDEX_ID));

      assertThat(FileBasedIndex.getInstance().getFileData(TrigramIndex.INDEX_ID, file, myFixture.getProject()).keySet()).isNullOrEmpty();

      assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(StubUpdatingIndex.INDEX_ID));
      assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(TrigramIndex.INDEX_ID));
    });
  }
}
