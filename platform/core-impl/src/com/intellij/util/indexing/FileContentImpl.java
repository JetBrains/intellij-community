// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LighterAST;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
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
public class FileContentImpl extends IndexedFileImpl implements PsiDependentFileContent {
  private Charset myCharset;
  private byte[] myContent;
  private CharSequence myContentAsText;
  private final long myStamp;
  private byte[] myHash;
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
                          boolean physicalContent) {
    super(file, FileTypeRegistry.getInstance().getFileTypeByFile(file, content));
    myContentAsText = contentAsText;
    myContent = content;
    myStamp = stamp;
    myPhysicalContent = physicalContent;
  }

  private static final Key<PsiFile> CACHED_PSI = Key.create("cached psi from content");

  @NotNull
  @Override
  public PsiFile getPsiFile() {
    return getPsiFileForPsiDependentIndex();
  }

  @NotNull
  private PsiFile getFileFromText() {
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

  @Override
  @NotNull
  public LighterAST getLighterAST() {
    LighterAST lighterAST = getUserData(IndexingDataKeys.LIGHTER_AST_NODE_KEY);
    if (lighterAST == null) {
      FileASTNode node = getPsiFile().getNode();
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
    FileType fileType = getFileTypeWithoutSubstitution();
    if (!(fileType instanceof LanguageFileType)) {
      throw new AssertionError("PSI can be created only for a file with LanguageFileType but actual is " + fileType.getClass()  + "." +
                               "\nPlease use a proper FileBasedIndexExtension#getInputFilter() implementation for the caller index");
    }
    return createFileFromText(project, text, (LanguageFileType)fileType, myFile, myFileName);
  }

  @NotNull
  public static PsiFile createFileFromText(@NotNull Project project, @NotNull CharSequence text, @NotNull LanguageFileType fileType,
                                           @NotNull VirtualFile file, @NotNull String fileName) {
    final Language language = fileType.getLanguage();
    final Language substitutedLanguage = LanguageSubstitutors.getInstance().substituteLanguage(language, file, project);
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, substitutedLanguage, text, false, false, false, file);
    if (psiFile == null) {
      throw new IllegalStateException("psiFile is null. language = " + language.getID() +
                                      ", substitutedLanguage = " + substitutedLanguage.getID());
    }
    return psiFile;
  }

  public static class IllegalDataException extends RuntimeException {
    IllegalDataException(final String message) {
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

  /**
   * @deprecated use {@link FileContent#getPsiFile()}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
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
      psi = getFileFromText();
    }
    return psi;
  }
}
