package com.intellij.localvcs.integration;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FileFilterTest {
  private TestVirtualFile f1 = new TestVirtualFile(null, null, null);
  private TestVirtualFile f2 = new TestVirtualFile(null, null, null);

  private FileType binaryType = createFileType(true);
  private FileType nonBinaryType = createFileType(false);

  private FileIndex fi = createMock(FileIndex.class);
  private FileTypeManager tm = createMock(FileTypeManager.class);

  @Test
  public void testFilteringFileFromAnotherProject() {
    expect(fi.isInContent(f1)).andReturn(true);
    expect(fi.isInContent(f2)).andReturn(false);
    replay(fi);

    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(nonBinaryType);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isFileAllowed(f1));
    assertFalse(f.isFileAllowed(f2));
  }

  @Test
  public void testFilteringFileOfUndesiredType() {
    expect(fi.isInContent((VirtualFile)anyObject())).andStubReturn(true);
    replay(fi);

    expect(tm.getFileTypeByFile(f1)).andStubReturn(nonBinaryType);
    expect(tm.getFileTypeByFile(f2)).andStubReturn(binaryType);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isFileAllowed(f1));
    assertFalse(f.isFileAllowed(f2));
  }

  @Test
  public void testFilteringDirectories() {
    f1 = new TestVirtualFile(null, null);
    f2 = new TestVirtualFile(null, null);

    expect(fi.isInContent(f1)).andReturn(true);
    expect(fi.isInContent(f2)).andReturn(false);
    replay(fi);

    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(binaryType);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isFileAllowed(f1));
    assertFalse(f.isFileAllowed(f2));
  }

  private FileType createFileType(boolean isBinary) {
    FileType t = createMock(FileType.class);
    expect(t.isBinary()).andReturn(isBinary);
    replay(t);
    return t;
  }
}
