package com.intellij.util.indexing;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * @author Eugene Zhuravlev
*         Date: Mar 28, 2008
*/
public final class FileContent extends UserDataHolderBase {
  private final VirtualFile myFile;
  private final String fileName;
  private final FileType myFileType;
  private Charset myCharset;
  private byte[] myContent;
  private CharSequence myContentAsText = null;

  public static class IllegalDataException extends RuntimeException{
    public IllegalDataException(final String message) {
      super(message);
    }

    public IllegalDataException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
  
  public FileContent(@NotNull final VirtualFile file, final @NotNull CharSequence contentAsText, final Charset charset) {
    this(file);
    myContentAsText = contentAsText;
    myCharset = charset;
  }
  
  public FileContent(@NotNull final VirtualFile file, @NotNull final byte[] content) {
    this(file);
    myContent = content;
    myCharset = LoadTextUtil.detectCharset(file, content);
  }

  public FileContent(@NotNull final VirtualFile file) {
    myFile = file;
    myFileType = FileTypeManager.getInstance().getFileTypeByFile(file);
    // remember name explicitly because the file could be renamed afterwards
    fileName = file.getName();
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public String getFileName() {
    return fileName;
  }

  public Charset getCharset() {
    return myCharset;
  }

  public byte[] getContent() {
    if (myContent == null) {
      if (myContentAsText != null) {
        try {
          myContent = myCharset != null? myContentAsText.toString().getBytes(myCharset.name()) : myContentAsText.toString().getBytes();
        }
        catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return myContent;
  }

  public CharSequence getContentAsText() {
    if (myFileType.isBinary()) {
      throw new IllegalDataException("Cannot obtain text for binary file type : " + myFileType.getDescription());
    }
    if (myContentAsText == null) {
      if (myContent != null) {
        myContentAsText = LoadTextUtil.getTextByBinaryPresentation(myContent, myCharset);
      }
    }
    return myContentAsText;
  }
}
