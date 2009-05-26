package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
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

  public Project getProject() {
    return getUserData(FileBasedIndex.PROJECT);
  }

  private final Key<PsiFile> CACHED_PSI = Key.create("cached psi from content");

  /**
   * @return psiFile associated with the content. If the file was not set on FileContentCreation, it will be created on the spot
   */
  public PsiFile getPsiFile() {
    PsiFile psi = getUserData(FileBasedIndex.PSI_FILE);

    if (psi == null) {
      psi = getUserData(CACHED_PSI);
    }

    if (psi == null) {
      Project project = getProject();
      if (project == null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          project = ProjectManager.getInstance().getDefaultProject();
        }
        else {
          throw new NoProjectForFileException();
        }
      }
      psi = PsiFileFactory.getInstance(project).createFileFromText(
        getFileName(),
        getFileType(),
        getContentAsText(),
        1,
        false,
        false
      );

      psi.putUserData(FileBasedIndex.VIRTUAL_FILE, getFile());
      putUserData(CACHED_PSI, psi);
    }
    return psi;
  }

  public static class IllegalDataException extends RuntimeException{
    public IllegalDataException(final String message) {
      super(message);
    }

    public IllegalDataException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
  
  public FileContent(@NotNull final VirtualFile file, @NotNull final CharSequence contentAsText, final Charset charset) {
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

  public FileType getFileType() {
    return myFileType;
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

  @Override
  public String toString() {
    return fileName;
  }
}
