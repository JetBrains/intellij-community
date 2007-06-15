package com.intellij.localvcs.integration;

import com.intellij.mock.MockDocument;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.*;

public class TestIdeaGateway extends IdeaGateway {
  private String myPhysicalContent;
  private List<MyDocument> myUnsavedDocuments = new ArrayList<MyDocument>();
  private Map<String, FileType> myFileTypes = new HashMap<String, FileType>();
  private List<VirtualFile> myContentRoots = new ArrayList<VirtualFile>();

  public TestIdeaGateway() {
    super(null);
  }

  public TestIdeaGateway(Project p) {
    super(p);
  }

  @Override
  protected FileFilter createFileFilter() {
    return new TestFileFilter();
  }

  public void setFileFilter(FileFilter f) {
    myFileFilter = f;
  }

  @Override
  public List<VirtualFile> getContentRoots() {
    return myContentRoots;
  }

  public void setContentRoots(VirtualFile... roots) {
    myContentRoots = Arrays.asList(roots);
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
    doAddDocument(new MyDocumentForFile(name, content, true));
  }

  public void addUnsavedDocumentForDeletedFile(String name, String content) {
    doAddDocument(new MyDocumentForFile(name, content, false));
  }

  public void addUnsavedDocumentWithoutFile(String name, String content) {
    doAddDocument(new MyDocument(name, content));
  }

  private void doAddDocument(MyDocument d) {
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

    public MyDocument(String name, String content) {
      myName = name;
      myContent = content;
    }

    @Override
    public String getText() {
      return myContent;
    }

    public VirtualFile getFile() {
      return null;
    }

    @Override
    public boolean equals(Object o) {
      return myName.equals(((MyDocument)o).myName);
    }
  }

  private class MyDocumentForFile extends MyDocument {
    private VirtualFile myFile;

    public MyDocumentForFile(String name, String content, final boolean isValid) {
      super(name, content);
      myFile = new TestVirtualFile(name, null, -1) {
        @Override
        public boolean isValid() {
          return isValid;
        }
      };
    }

    @Override
    public VirtualFile getFile() {
      return myFile;
    }
  }
}
