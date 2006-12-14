package com.intellij.localvcs.integration;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.localvcs.integration.stubs.StubFileTypeManager;
import com.intellij.mock.MockFileTypeManager;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FileFilterTest {
  private TestVirtualFile f1 = new TestVirtualFile(null, null, null);
  private TestVirtualFile f2 = new TestVirtualFile(null, null, null);

  private FileIndex fi = createMock(FileIndex.class);
  private MyFileTypeManager tm = new MyFileTypeManager();

  @Test
  public void testFilteringFileFromAnotherProject() {
    expect(fi.isInContent(f1)).andReturn(true);
    expect(fi.isInContent(f2)).andReturn(false);
    replay(fi);

    tm.setDefaultFileType(createFileType(false));

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isFileAllowed(f1));
    assertFalse(f.isFileAllowed(f2));
  }

  @Test
  public void testFilteringFileOfUndesiredType() {
    expect(fi.isInContent((VirtualFile)anyObject())).andStubReturn(true);
    replay(fi);

    tm.setFileType(f1, createFileType(false));
    tm.setFileType(f2, createFileType(true));

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

    tm.setDefaultFileType(createFileType(true));

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

  private class MyFileTypeManager extends MockFileTypeManager {
    private Map<VirtualFile, FileType> myTypes = new HashMap<VirtualFile, FileType>();
    private FileType myDefaultFileType;

    @Override
    public FileType getFileTypeByFile(VirtualFile f) {
      FileType result = myTypes.get(f);
      return result == null ? myDefaultFileType : result;
    }

    public void setFileType(VirtualFile f, FileType t) {
      myTypes.put(f, t);
    }

    public void setDefaultFileType(FileType t) {
      myDefaultFileType = t;
    }
  }
}
