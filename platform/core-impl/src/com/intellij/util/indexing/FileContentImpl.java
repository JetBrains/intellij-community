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
  private byte[] myContent;
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
  @NotNull
  public LighterAST getLighterAST() {
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
  void ensureThreadSafeLighterAST() {
    myLighterASTShouldBeThreadSafe = true;
  }

  private PsiFile createFileFromText(@NotNull CharSequence text) {
    Project project = getProject();
    if (project == null) {
      project = DefaultProjectFactory.getInstance().getDefaultProject();
    }
    FileType fileType = getFileTypeWithoutSubstitution(this);
    if (!(fileType instanceof LanguageFileType)) {
      throw new AssertionError("PSI can be created only for a file with LanguageFileType but actual is " + fileType.getClass() + "." +
                               "\nPlease use a proper FileBasedIndexExtension#getInputFilter() implementation for the caller index");
    }
    return createFileFromText(project, text, (LanguageFileType)fileType, myFile, getFileName());
  }

  @NotNull
  public static PsiFile createFileFromText(@NotNull Project project, @NotNull CharSequence text, @NotNull LanguageFileType fileType,
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

  public static @NotNull FileContent createByText(@NotNull final VirtualFile file, @NotNull final CharSequence contentAsText, @Nullable Project project) {
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

  @NotNull
  public Charset getCharset() {
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
    if (myContent == null) {
      if (myContentAsText != null) {
        myContent = myContentAsText.toString().getBytes(getCharset());
      } else {
        myContent = myContentComputable.compute();
        FileType unsubstitutedFileType = getFileTypeWithoutSubstitution(this);
        if (!unsubstitutedFileType.isBinary()) {
          // Normalize line-separators for textual files to ensure
          // consistency of getContent() and getContentAsText(): both must return \n.
          // It calls getContent() internally and assigns the myContent to null.
          myContent = getContentAsText().toString().getBytes(getCharset());
        }
      }
    }
    return myContent;
  }

  @NotNull
  @Override
  public CharSequence getContentAsText() {
    FileType unsubstitutedFileType = getFileTypeWithoutSubstitution(this);
    if (unsubstitutedFileType.isBinary()) {
      throw new UnsupportedOperationException("Cannot obtain text for binary file type : " + unsubstitutedFileType.getDescription());
    }
    final CharSequence content = getUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY);
    try {
      if (content != null) {
        return content;
      }
      if (myContentAsText == null) {
        myContentAsText = LoadTextUtil.getTextByBinaryPresentation(getContent(), myFile);
      }
      return myContentAsText;
    } finally {
      // Help GC. Indexes expect either getContent() or getContentAsText().
      myContent = null;
    }
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
  @NotNull
  public PsiFile getPsiFile() {
    if (myTransientContent) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(getFile());

      if (document != null) {
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
        if (psiDocumentManager.isUncommited(document)) {
          PsiFile existingPsi = psiDocumentManager.getPsiFile(document);
          if (existingPsi != null) {
            return existingPsi;
          }
        }
      }
    }
    PsiFile explicitPsi = getUserData(IndexingDataKeys.PSI_FILE);
    if (explicitPsi != null) {
      return explicitPsi;
    }
    PsiFile cachedPsi = getUserData(CACHED_PSI);
    if (cachedPsi != null) {
      return cachedPsi;
    }
    PsiFile createdPsi = createFileFromText(getContentAsText());
    createdPsi.putUserData(IndexingDataKeys.VIRTUAL_FILE, getFile());
    putUserData(CACHED_PSI, createdPsi);
    return createdPsi;
  }

  @ApiStatus.Internal
  @NotNull
  public static FileType getFileTypeWithoutSubstitution(@NotNull IndexedFile indexedFile) {
    FileType fileType = indexedFile.getFileType();
    return fileType instanceof SubstitutedFileType ? ((SubstitutedFileType)fileType).getOriginalFileType() : fileType;
  }
}
