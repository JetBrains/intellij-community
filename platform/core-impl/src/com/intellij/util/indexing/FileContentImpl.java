// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LighterAST;
import com.intellij.lang.TreeBackedLighterAST;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

public final class FileContentImpl extends IndexedFileImpl implements PsiDependentFileContent {
  private final @NotNull NotNullComputable<byte[]> myContentComputable;
  private Charset myCharset;
  private byte[] myCachedContentBytes;
  private CharSequence myContentAsText;
  private byte[] myIndexedFileHash;
  private boolean myLighterASTShouldBeThreadSafe;
  private final boolean myTransientContent;

  private FileContentImpl(@NotNull VirtualFile file,
                          @NotNull FileType fileType,
                          @Nullable CharSequence contentAsText,
                          @NotNull NotNullComputable<byte[]> contentComputable,
                          boolean transientContent) {
    super(file, fileType, null);
    myContentAsText = contentAsText;
    myContentComputable = contentComputable;
    myTransientContent = transientContent;
  }

  private static final Key<PsiFile> CACHED_PSI = Key.create("cached psi from content");

  private static final Key<LighterAST> LIGHTER_AST_NODE_KEY = Key.create("lighter.ast.node");

  @Override
  public @NotNull LighterAST getLighterAST() {
    LighterAST lighterAST = getUserData(LIGHTER_AST_NODE_KEY);
    if (lighterAST == null) {
      FileASTNode node = getPsiFile().getNode();
      lighterAST = myLighterASTShouldBeThreadSafe ? new TreeBackedLighterAST(node) : node.getLighterAST();
      putUserData(LIGHTER_AST_NODE_KEY, lighterAST);
    }
    return lighterAST;
  }

  /**
   * Expand the AST to ensure {@link com.intellij.lang.FCTSBackedLighterAST} won't be used, because it's not thread-safe,
   * but unsaved documents may be indexed in many concurrent threads
   */
  @ApiStatus.Internal
  public void ensureThreadSafeLighterAST() {
    myLighterASTShouldBeThreadSafe = true;
  }

  private PsiFile createFileFromText(@NotNull CharSequence text) {
    Project project = getProject();
    FileType fileType = getFileTypeWithoutSubstitution(this);
    if (!(fileType instanceof LanguageFileType)) {
      throw new AssertionError("PSI can be created only for a file with LanguageFileType but actual is " + fileType.getClass() + "." +
                               "\nPlease use a proper FileBasedIndexExtension#getInputFilter() implementation for the caller index");
    }
    return createFileFromText(project, text, (LanguageFileType)fileType, myFile, getFileName());
  }

  public static @NotNull PsiFile createFileFromText(@NotNull Project project, @NotNull CharSequence text, @NotNull LanguageFileType fileType,
                                                    @NotNull VirtualFile file, @NotNull String fileName) {
    final Language language = fileType.getLanguage();
    final Language substitutedLanguage = LanguageSubstitutors.getInstance().substituteLanguage(language, file, project);
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
      fileName, substitutedLanguage, text, false, false, false, file
    );
    if (psiFile == null) {
      throw new IllegalStateException("psiFile is null. language = " + language.getID() +
                                      ", substitutedLanguage = " + substitutedLanguage.getID());
    }
    return psiFile;
  }

  public static @NotNull FileContent createByContent(@NotNull VirtualFile file, byte @NotNull [] content) {
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFile(file, content);
    return new FileContentImpl(file, fileType, null, () -> content, false);
  }

  public static @NotNull FileContentImpl createByContent(@NotNull VirtualFile file,
                                                         @NotNull NotNullComputable<byte[]> contentComputable) {
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFile(file);
    return new FileContentImpl(file, fileType, null, contentComputable, false);
  }

  public static @NotNull FileContent createByContent(@NotNull VirtualFile file,
                                                     @NotNull NotNullComputable<byte[]> contentComputable,
                                                     @Nullable Project project) {
    FileContentImpl fileContent = createByContent(file, contentComputable);
    if (project != null) {
      fileContent.setProject(project);
    }
    return fileContent;
  }

  public static @NotNull FileContent createByFile(@NotNull VirtualFile file) throws IOException {
    return createByFile(file, null);
  }

  public static @NotNull FileContent createByFile(@NotNull VirtualFile file, @Nullable Project project) throws IOException {
    FileContentImpl content = (FileContentImpl)createByContent(file, file.contentsToByteArray(false));
    if (project != null) {
      content.setProject(project);
    }
    return content;
  }

  public static @NotNull FileContent createByText(final @NotNull VirtualFile file, final @NotNull CharSequence contentAsText, @Nullable Project project) {
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFile(file);
    FileContentImpl content = new FileContentImpl(file,
                                                  fileType,
                                                  contentAsText,
                                                  () -> {
                                                    throw new IllegalStateException("Content must be converted from 'contentAsText'");
                                                  },
                                                  true);
    if (project != null) {
      content.setProject(project);
    }
    return content;
  }

  public @NotNull Charset getCharset() {
    Charset charset = myCharset;
    if (charset == null) {
      myCharset = charset = myFile.getCharset();
    }
    return charset;
  }

  public boolean isTransientContent() {
    return myTransientContent;
  }

  @Override
  public byte @NotNull [] getContent() {
    if (myCachedContentBytes == null) {
      FileType unsubstitutedFileType = getFileTypeWithoutSubstitution(this);
      if (unsubstitutedFileType.isBinary()) {
        myCachedContentBytes = computeOriginalContent();
      }
      else {
        // Normalize line-separators for textual files to ensure
        // consistency of getContent() and getContentAsText(): both must return \n.
        myCachedContentBytes = getContentAsText().toString().getBytes(getCharset());
      }
    }
    return myCachedContentBytes;
  }

  @Override
  public @NotNull CharSequence getContentAsText() {
    FileType unsubstitutedFileType = getFileTypeWithoutSubstitution(this);
    if (unsubstitutedFileType.isBinary()) {
      throw new UnsupportedOperationException("Cannot obtain text for binary file type : " + unsubstitutedFileType.getDescription());
    }
    final CharSequence content = getUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY);
    if (content != null) {
      return content;
    }
    if (myContentAsText == null) {
      myContentAsText = LoadTextUtil.getTextByBinaryPresentation(computeOriginalContent(), myFile, false, false);
    }
    return myContentAsText;
  }

  private byte @NotNull [] computeOriginalContent() {
    return myContentComputable.compute();
  }

  @Override
  public String toString() {
    return "FileContentImpl(" + getFileName() + ")";
  }

  public byte @Nullable [] getIndexedFileHash() {
    if (myTransientContent) {
      throw new IllegalStateException("Hashes are allowed only while physical changes indexing");
    }
    return myIndexedFileHash;
  }

  public void setIndexedFileHash(byte @NotNull [] fileContentHash) {
    myIndexedFileHash = fileContentHash;
  }

  @Override
  public @NotNull PsiFile getPsiFile() {
    if (myTransientContent) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(getFile());

      if (document != null) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
        if (psiDocumentManager.isUncommited(document)) {
          PsiFile existingPsi = psiDocumentManager.getPsiFile(document);
          if (existingPsi != null) {
            return checkPsiProjectConsistency(existingPsi);
          }
        }
      }
    }
    PsiFile explicitPsi = getUserData(IndexingDataKeys.PSI_FILE);
    if (explicitPsi != null) {
      return checkPsiProjectConsistency(explicitPsi);
    }
    PsiFile cachedPsi = getUserData(CACHED_PSI);
    if (cachedPsi != null) {
      return checkPsiProjectConsistency(cachedPsi);
    }
    PsiFile createdPsi = createFileFromText(getContentAsText());
    createdPsi.putUserData(IndexingDataKeys.VIRTUAL_FILE, getFile());
    putUserData(CACHED_PSI, createdPsi);
    return checkPsiProjectConsistency(createdPsi);
  }

  private @NotNull PsiFile checkPsiProjectConsistency(@NotNull PsiFile file) {
    if (!file.getProject().equals(getProject())) {
      Logger.getInstance(FileContentImpl.class).error("psi file's project is not equal to file content's project");
    }
    return file;
  }

  @ApiStatus.Internal
  public static @NotNull FileType getFileTypeWithoutSubstitution(@NotNull IndexedFile indexedFile) {
    FileType fileType = indexedFile.getFileType();
    return fileType instanceof SubstitutedFileType ? ((SubstitutedFileType)fileType).getOriginalFileType() : fileType;
  }
}
