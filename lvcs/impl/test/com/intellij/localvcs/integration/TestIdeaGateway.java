package com.intellij.localvcs.integration;

import com.intellij.mock.MockDocument;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class TestIdeaGateway extends IdeaGateway {
  private String myPhysicalContent;
  private List<MyDocument> myUnsavedDocuments = new ArrayList<MyDocument>();
  private Map<String, FileType> myFileTypes = new HashMap<String, FileType>();

  public TestIdeaGateway() {
    super(null);
  }

  @Override
  protected FileFilter createFileFilter() {
    return new TestFileFilter();
  }

  public void setFileFilter(FileFilter f) {
    myFileFilter = f;
  }

  @Override
  public <T> T performCommandInsideWriteAction(String name, Callable<T> c) {
    try {
      return c.call();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean ensureFilesAreWritable(VirtualFile... ff) {
    return true;
  }

  @Override
  public byte[] getPhysicalContent(VirtualFile f) throws IOException {
    if (myPhysicalContent == null) return f.contentsToByteArray();
    return myPhysicalContent.getBytes();
  }

  @Override
  public long getPhysicalLength(VirtualFile f) throws IOException {
    return getPhysicalContent(f).length;
  }

  public void setPhysicalContent(String c) {
    myPhysicalContent = c;
  }

  @Override
  public Document[] getUnsavedDocuments() {
    return myUnsavedDocuments.toArray(new Document[0]);
  }

  public void addUnsavedDocument(String name, String content) {
    MyDocument d = new MyDocument(name, content);
    myUnsavedDocuments.remove(d);
    myUnsavedDocuments.add(d);
  }

  @Override
  public VirtualFile getDocumentFile(Document d) {
    return ((MyDocument)d).getFile();
  }

  public VirtualFile[] getUnsavedDocumentFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (MyDocument d : myUnsavedDocuments) {
      result.add(d.getFile());
    }
    return result.toArray(new VirtualFile[0]);
  }

  @Override
  public FileType getFileType(String fileName) {
    return myFileTypes.get(fileName);
  }

  public void addFileType(String fileName, FileType t) {
    myFileTypes.put(fileName, t);
  }

  private class MyDocument extends MockDocument {
    private String myName;
    private String myContent;
    private VirtualFile myFile;

    public MyDocument(String name, String content) {
      myName = name;
      myContent = content;
      myFile = new TestVirtualFile(myName, null, -1);
    }

    @Override
    public String getText() {
      return myContent;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Override
    public boolean equals(Object o) {
      return myName.equals(((MyDocument)o).myName);
    }
  }
}
