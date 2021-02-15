// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.FilenameIndexImpl;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.text.CharArrayCharSequence;

/**
 * @author Dmitry Avdeev
 */
public class FileBasedIndexTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSurviveOnFileTypeChange() {
    myFixture.configureByText("Foo.java", "class Foo { String bar; }");
    myFixture.testHighlighting();
    FileType foo = FileTypeIndexTest.registerFakeFileType();
    FileTypeManagerEx.getInstanceEx().unregisterFileType(foo);
    myFixture.configureByText("Bar.java", "class Bar { String bar; }");
    myFixture.testHighlighting();
  }

  public void testLargeFile() {
    String largeFileText = "class Foo { String bar; }" + StringUtil.repeat(" ", FileUtilRt.LARGE_FOR_CONTENT_LOADING + 42);
    VirtualFile file = myFixture.addFileToProject("Foo.java", largeFileText).getVirtualFile();
    int fileId = ((VirtualFileWithId)file).getId();

    assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(StubUpdatingIndex.INDEX_ID));
    assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(TrigramIndex.INDEX_ID));

    assertEmpty(FileBasedIndex.getInstance().getFileData(TrigramIndex.INDEX_ID, file, getProject()).keySet());

    assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(StubUpdatingIndex.INDEX_ID));
    assertFalse(IndexingStamp.getNontrivialFileIndexedStates(fileId).contains(TrigramIndex.INDEX_ID));
  }
}
