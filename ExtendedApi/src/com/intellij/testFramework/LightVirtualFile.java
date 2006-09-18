package com.intellij.testFramework;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.*;
import com.intellij.util.LocalTimeCounter;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class LightVirtualFile extends VirtualFile {
  private FileType myFileType;
  protected CharSequence myContent = "";
  protected String myName = "";
  public long myModStamp = LocalTimeCounter.currentTime();
  private boolean myIsWritable = true;
  private VirtualFileListener myListener = null;
  @NonNls private static final Charset CHARSET = Charset.forName(CharsetToolkit.UTF8);

  public LightVirtualFile() {
  }

  public LightVirtualFile(@NonNls String name) {
    myName = name;
  }

  public LightVirtualFile(@NonNls String name, CharSequence content) {
    myName = name;
    myContent = content;
  }

  public LightVirtualFile(final String name, final FileType fileType, final CharSequence text) {
    this(name, fileType, text, LocalTimeCounter.currentTime());
  }

  public LightVirtualFile(final String name, final FileType fileType, final CharSequence text, final long modificationStamp) {
    myName = name;
    myFileType = fileType;
    myContent = text;
    myModStamp = modificationStamp;
  }

  public LightVirtualFile(final String name, final Language language, final String text) {
    myName = name;
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (final FileType fileType : fileTypes) {
      if(fileType instanceof LanguageFileType){
        final LanguageFileType languageFileType = (LanguageFileType)fileType;
        if(languageFileType.getLanguage() == language){
          myFileType = languageFileType;
          break;
        }
      }
    }
    if(myFileType == null) myFileType = language.getAssociatedFileType();
    if(myFileType == null) myFileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    myContent = text;
    myModStamp = LocalTimeCounter.currentTime();
  }

  public void setListener(VirtualFileListener listener) {
    myListener = listener;
  }

  private static class MyVirtualFileSystem extends VirtualFileSystem {
    @NonNls private final static String PROTOCOL = "mock";

    public String getProtocol() {
      return PROTOCOL;
    }

    @Nullable
    public VirtualFile findFileByPath(String path) {
      return null;
    }

    public void refresh(boolean asynchronous) {}

    @Nullable
    public VirtualFile refreshAndFindFileByPath(String path) {
      return null;
    }

    public void forceRefreshFiles(final boolean asynchronous, @NotNull VirtualFile... files) {}

    protected void deleteFile(Object requestor, VirtualFile vFile) throws IOException {}

    protected void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {}

    protected void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {}

    protected VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
      throw new IOException("Cannot create files");
    }

    protected VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
      throw new IOException("Cannot create directories");
    }
  }

  private static MyVirtualFileSystem ourFileSystem = new MyVirtualFileSystem();

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  @NotNull
  public FileType getFileType() {
    return myFileType != null ? myFileType : super.getFileType();
  }

  public String getPath() {
    return "/" + getName();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public boolean isWritable() {
    return myIsWritable;
  }

  public boolean isDirectory() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public VirtualFile getParent() {
    return null;
  }

  public VirtualFile[] getChildren() {
    return VirtualFile.EMPTY_ARRAY;
  }

  public InputStream getInputStream() throws IOException {
    throw new IOException("Cannot get input stream");
  }

  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      public void close() throws IOException {
        myModStamp = newModificationStamp;
        myContent = toString();
      }
    };
  }

  public byte[] contentsToByteArray() throws IOException {
    return getContent().toString().getBytes(getCharset().name());
  }

  public long getModificationStamp() {
    return myModStamp;
  }

  public long getTimeStamp() {
    return 0; // todo[max] : Add UnsupporedOperationException at better times.
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    }
    catch (IOException e) {
      e.printStackTrace();
      Assert.assertTrue(false);
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  public void setContent(Object requestor, CharSequence content, boolean fireEvent) {
    myContent = content;
    if (fireEvent) {
      long oldStamp = myModStamp;
      myModStamp = LocalTimeCounter.currentTime();
      myListener.contentsChanged(new VirtualFileEvent(requestor, this, null, oldStamp, myModStamp));
    }
  }

  public VirtualFile self() {
    return this;
  }

  public void setWritable(boolean b) {
    myIsWritable = b;
  }

  public void rename(Object requestor, @NotNull String newName) throws IOException {
    myName = newName;
  }

  public CharSequence getContent() {
    return myContent;
  }

  public Charset getCharset() {
    return CHARSET;
  }
}
