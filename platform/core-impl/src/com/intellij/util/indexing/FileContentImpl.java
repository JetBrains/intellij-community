/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing;

import com.intellij.lang.Language;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * @author nik
 */
public final class FileContentImpl extends UserDataHolderBase implements FileContent {
  private final VirtualFile myFile;
  private final String myFileName;
  private final FileType myFileType;
  private final Charset myCharset;
  private byte[] myContent;
  private CharSequence myContentAsText;

  @Override
  public Project getProject() {
    return getUserData(IndexingDataKeys.PROJECT);
  }

  private static final Key<PsiFile> CACHED_PSI = Key.create("cached psi from content");

  /**
   * @return psiFile associated with the content. If the file was not set on FileContentCreation, it will be created on the spot
   */
  @NotNull
  @Override
  public PsiFile getPsiFile() {
    PsiFile psi = getUserData(IndexingDataKeys.PSI_FILE);

    if (psi == null) {
      psi = getUserData(CACHED_PSI);
    }

    if (psi == null) {
      Project project = getProject();
      if (project == null) {
        project = DefaultProjectFactory.getInstance().getDefaultProject();
      }
      final Language language = ((LanguageFileType)getFileTypeWithoutSubstitution()).getLanguage();
      final Language substitutedLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(language, getFile(), project);
      psi = PsiFileFactory.getInstance(project).createFileFromText(getFileName(), substitutedLanguage, getContentAsText(), false, false, true);

      psi.putUserData(IndexingDataKeys.VIRTUAL_FILE, getFile());
      putUserData(CACHED_PSI, psi);
    }
    return psi;
  }

  public static class IllegalDataException extends RuntimeException {
    public IllegalDataException(final String message) {
      super(message);
    }
  }

  public FileContentImpl(@NotNull final VirtualFile file, @NotNull final CharSequence contentAsText, final Charset charset) {
    this(file, contentAsText, null, charset);
  }

  public FileContentImpl(@NotNull final VirtualFile file, @NotNull final byte[] content) {
    this(file, null, content, LoadTextUtil.detectCharsetAndSetBOM(file, content));
  }

  public FileContentImpl(@NotNull final VirtualFile file) {
    this(file, null, null, null);
  }

  private FileContentImpl(@NotNull VirtualFile file, CharSequence contentAsText, byte[] content, Charset charset) {
    myFile = file;
    myContentAsText = contentAsText;
    myContent = content;
    myCharset = charset;
    myFileType = file.getFileType();
    // remember name explicitly because the file could be renamed afterwards
    myFileName = file.getName();
  }

  @NotNull
  private FileType substituteFileType(VirtualFile file, FileType fileType) {
    Project project = getProject();
    return SubstitutedFileType.substituteFileType(file, fileType, project);
  }

  @NotNull
  public FileType getSubstitutedFileType() {
    return substituteFileType(myFile, myFileType);
  }

  @TestOnly
  public static FileContent createByFile(@NotNull VirtualFile file) {
    try {
      return new FileContentImpl(file, file.contentsToByteArray());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public FileType getFileTypeWithoutSubstitution() {
    return myFileType;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return getSubstitutedFileType();
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  @Override
  public String getFileName() {
    return myFileName;
  }

  public Charset getCharset() {
    return myCharset;
  }

  @Override
  public byte[] getContent() {
    if (myContent == null) {
      if (myContentAsText != null) {
        try {
          myContent = myCharset != null ? myContentAsText.toString().getBytes(myCharset.name()) : myContentAsText.toString().getBytes();
        }
        catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return myContent;
  }

  @Override
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
    return myFileName;
  }
}
