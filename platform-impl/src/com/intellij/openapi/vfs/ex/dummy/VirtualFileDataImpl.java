package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;

import java.io.*;

/**
 *
 */
class VirtualFileDataImpl extends VirtualFileImpl {
  private byte[] myContents = ArrayUtil.EMPTY_BYTE_ARRAY;
  private long myModificationStamp = LocalTimeCounter.currentTime();

  public VirtualFileDataImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    super(fileSystem, parent, name);
  }

  public boolean isDirectory() {
    return false;
  }

  public long getLength() {
    return myContents.length;
  }

  public VirtualFile[] getChildren() {
    return null;
  }

  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(myContents);
  }

  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    return new OutputStream() {
      public void write(int b) throws IOException {
        out.write(b);
      }

      public void write(byte[] b) throws IOException {
        out.write(b);
      }

      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      public void flush() throws IOException {
        out.flush();
      }

      public void close() throws IOException {
        out.close();
        final DummyFileSystem fs = (DummyFileSystem)getFileSystem();
        fs.fireBeforeContentsChange(requestor, VirtualFileDataImpl.this);
        final long oldModStamp = myModificationStamp;
        myContents = out.toByteArray();
        myModificationStamp = newModificationStamp >= 0 ? newModificationStamp : LocalTimeCounter.currentTime();
        fs.fireContentsChanged(requestor, VirtualFileDataImpl.this, oldModStamp);
      }
    };
  }

  public byte[] contentsToByteArray() throws IOException {
    return myContents;
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp, Object requestor) {
    myModificationStamp = modificationStamp;
  }
}
