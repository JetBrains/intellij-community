/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LighterAST;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author nik
 *
 * Class is not final since it is overridden in Upsource
 */
public class FileContentImpl extends UserDataHolderBase implements FileContent {
  protected final VirtualFile myFile;
  protected final String myFileName;
  protected final FileType myFileType;
  protected Charset myCharset;
  protected byte[] myContent;
  protected CharSequence myContentAsText;
  protected final long myStamp;
  protected byte[] myHash;
  private boolean myLighterASTShouldBeThreadSafe;
  private final boolean myPhysicalContent;

  public FileContentImpl(@NotNull final VirtualFile file, @NotNull final CharSequence contentAsText, long documentStamp) {
    this(file, contentAsText, null, documentStamp, false);
  }

  public FileContentImpl(@NotNull final VirtualFile file, @NotNull final byte[] content) {
    this(file, null, content, -1, true);
  }

  FileContentImpl(@NotNull final VirtualFile file) {
    this(file, null, null, -1, true);
  }

  private FileContentImpl(@NotNull VirtualFile file,
                          CharSequence contentAsText,
                          byte[] content,
                          long stamp,
                          boolean physicalContent
                          ) {
    myFile = file;
    myContentAsText = contentAsText;
    myContent = content;
    myFileType = file.getFileType();
    // remember name explicitly because the file could be renamed afterwards
    myFileName = file.getName();
    myStamp = stamp;
    myPhysicalContent = physicalContent;
  }

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
      psi = createFileFromText(getContentAsText());
      psi.putUserData(IndexingDataKeys.VIRTUAL_FILE, getFile());
      putUserData(CACHED_PSI, psi);
    }
    return psi;
  }

  @NotNull
  public LighterAST getLighterASTForPsiDependentIndex() {
    LighterAST lighterAST = getUserData(IndexingDataKeys.LIGHTER_AST_NODE_KEY);
    if (lighterAST == null) {
      FileASTNode node = getPsiFileForPsiDependentIndex().getNode();
      lighterAST = myLighterASTShouldBeThreadSafe ? new TreeBackedLighterAST(node) : node.getLighterAST();
      putUserData(IndexingDataKeys.LIGHTER_AST_NODE_KEY, lighterAST);
    }
    return lighterAST;
  }

  /**
   * Expand the AST to ensure {@link com.intellij.lang.FCTSBackedLighterAST} won't be used, because it's not thread-safe,
   * but unsaved documents may be indexed in many concurrent threads
   */
  void ensureThreadSafeLighterAST() {
    myLighterASTShouldBeThreadSafe = true;
  }

  public PsiFile createFileFromText(@NotNull CharSequence text) {
    Project project = getProject();
    if (project == null) {
      project = DefaultProjectFactory.getInstance().getDefaultProject();
    }
    return createFileFromText(project, text, (LanguageFileType)getFileTypeWithoutSubstitution(), myFile, myFileName);
  }

  @NotNull
  public static PsiFile createFileFromText(@NotNull Project project, @NotNull CharSequence text, @NotNull LanguageFileType fileType,
                                           @NotNull VirtualFile file, @NotNull String fileName) {
    final Language language = fileType.getLanguage();
    final Language substitutedLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(language, file, project);
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, substitutedLanguage, text, false, false, true, file);
    if (psiFile == null) {
      throw new IllegalStateException("psiFile is null. language = " + language.getID() +
                                      ", substitutedLanguage = " + substitutedLanguage.getID());
    }
    return psiFile;
  }

  public static class IllegalDataException extends RuntimeException {
    public IllegalDataException(final String message) {
      super(message);
    }
  }

  @NotNull
  private FileType getSubstitutedFileType() {
    return SubstitutedFileType.substituteFileType(myFile, myFileType, getProject());
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

  private FileType getFileTypeWithoutSubstitution() {
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

  @NotNull
  public Charset getCharset() {
    Charset charset = myCharset;
    if (charset == null) {
      myCharset = charset = myFile.getCharset();
    }
    return charset;
  }

  public long getStamp() {
    return myStamp;
  }

  @NotNull
  @Override
  public byte[] getContent() {
    byte[] content = myContent;
    if (content == null) {
      myContent = content = myContentAsText.toString().getBytes(getCharset());
    }
    return content;
  }

  @NotNull
  @Override
  public CharSequence getContentAsText() {
    if (myFileType.isBinary()) {
      throw new IllegalDataException("Cannot obtain text for binary file type : " + myFileType.getDescription());
    }
    final CharSequence content = getUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY);
    if (content != null) {
      return content;
    }
    CharSequence contentAsText = myContentAsText;
    if (contentAsText == null) {
      myContentAsText = contentAsText = LoadTextUtil.getTextByBinaryPresentation(myContent, myFile);
      myContent = null; // help gc, indices are expected to use bytes or chars but not both
    }
    return contentAsText;
  }

  @Override
  public String toString() {
    return myFileName;
  }

  @Nullable
  public byte[] getHash() {
    return myHash;
  }

  public void setHash(byte[] hash) {
    myHash = hash;
  }

  @NotNull
  public PsiFile getPsiFileForPsiDependentIndex() {
    PsiFile psi = null;
    if (!myPhysicalContent) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(getFile());

      if (document != null) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
        if (psiDocumentManager.isUncommited(document)) {
          PsiFile existingPsi = psiDocumentManager.getPsiFile(document);
          if (existingPsi != null) {
            psi = existingPsi;
          }
        }
      }
    }
    if (psi == null) {
      psi = getPsiFile();
    }
    return psi;
  }
}
