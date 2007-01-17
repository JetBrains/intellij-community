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

  private FileType binary = createFileType(true);
  private FileType nonBinary = createFileType(false);

  private FileIndex fi = createMock(FileIndex.class);
  private FileTypeManager tm = createMock(FileTypeManager.class);

  @Test
  public void testFilteringFileFromAnotherProject() {
    expect(fi.isInContent(f1)).andReturn(true);
    expect(fi.isInContent(f2)).andReturn(false);
    replay(fi);

    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(nonBinary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isFileAllowed(f1));
    assertFalse(f.isFileAllowed(f2));
  }

  @Test
  public void testFilteringFileOfUndesiredType() {
    expect(fi.isInContent((VirtualFile)anyObject())).andStubReturn(true);
    replay(fi);

    expect(tm.getFileTypeByFile(f1)).andStubReturn(nonBinary);
    expect(tm.getFileTypeByFile(f2)).andStubReturn(binary);
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

    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(binary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isFileAllowed(f1));
    assertFalse(f.isFileAllowed(f2));
  }

  @Test
  public void testFilteringBigFiles() {
    VirtualFile big = new TestVirtualFile(null, null, null, 100L * 1024L);
    VirtualFile small = new TestVirtualFile(null, null, null, 99L * 1024L);
    VirtualFile dir = new TestVirtualFile(null, null);

    expect(fi.isInContent((VirtualFile)anyObject())).andStubReturn(true);
    replay(fi);

    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(nonBinary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertFalse(f.isFileAllowed(big));
    assertTrue(f.isFileAllowed(small));
    assertTrue(f.isFileAllowed(dir));
  }

  private FileType createFileType(boolean isBinary) {
    FileType t = createMock(FileType.class);
    expect(t.isBinary()).andStubReturn(isBinary);
    replay(t);
    return t;
  }
}
