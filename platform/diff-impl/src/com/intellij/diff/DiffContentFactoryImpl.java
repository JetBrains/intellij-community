/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff;

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.diff.actions.DocumentFragmentContent;
import com.intellij.diff.contents.*;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.LightColors;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class DiffContentFactoryImpl extends DiffContentFactoryEx {
  private static final Logger LOG = Logger.getInstance(DiffContentFactoryImpl.class);

  @NotNull
  @Override
  public EmptyContent createEmpty() {
    return new EmptyContent();
  }


  @NotNull
  @Override
  public DocumentContent create(@NotNull String text) {
    return create(null, text);
  }

  @NotNull
  @Override
  public DocumentContent create(@NotNull String text, @Nullable FileType type) {
    return create(null, text, type);
  }

  @NotNull
  @Override
  public DocumentContent create(@NotNull String text, @Nullable FileType type, boolean respectLineSeparators) {
    return create(null, text, type, respectLineSeparators);
  }

  @NotNull
  @Override
  public DocumentContent create(@NotNull String text, @Nullable VirtualFile highlightFile) {
    return create(null, text, highlightFile);
  }

  @NotNull
  @Override
  public DocumentContent create(@NotNull String text, @Nullable DocumentContent referent) {
    return create(null, text, referent);
  }


  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text) {
    return create(project, text, (FileType)null);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type) {
    return create(project, text, type, true);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type, boolean respectLineSeparators) {
    return createImpl(project, text, type, null, null, respectLineSeparators, true);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @NotNull FilePath filePath) {
    return createImpl(project, text, filePath.getFileType(), filePath.getName(), filePath.getVirtualFile(), true, true);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable VirtualFile highlightFile) {
    FileType fileType = highlightFile != null ? highlightFile.getFileType() : null;
    String fileName = highlightFile != null ? highlightFile.getName() : null;
    return createImpl(project, text, fileType, fileName, highlightFile, true, true);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable DocumentContent referent) {
    if (referent == null) return create(text);
    return createImpl(project, text, referent.getContentType(), null, referent.getHighlightFile(), false, true);
  }


  @NotNull
  @Override
  public DocumentContent create(@NotNull Document document, @Nullable DocumentContent referent) {
    return create(null, document, referent);
  }


  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull Document document) {
    return create(project, document, (FileType)null);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable FileType fileType) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) return new FileDocumentContentImpl(project, document, file);
    return new DocumentContentImpl(project, document, fileType, null, null, null, null);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable VirtualFile highlightFile) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && file.equals(highlightFile)) return new FileDocumentContentImpl(project, document, file);
    if (highlightFile == null) return new DocumentContentImpl(document);
    return new DocumentContentImpl(project, document, highlightFile.getFileType(), highlightFile, null, null, null);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable DocumentContent referent) {
    if (referent == null) return new DocumentContentImpl(document);
    return new DocumentContentImpl(project, document, referent.getContentType(), referent.getHighlightFile(), null, null, null);
  }


  @NotNull
  @Override
  public DiffContent create(@Nullable Project project, @NotNull VirtualFile file) {
    return createContentFromFile(project, file);
  }

  @Nullable
  @Override
  public DocumentContent createDocument(@Nullable Project project, @NotNull final VirtualFile file) {
    return ObjectUtils.tryCast(createContentFromFile(project, file), DocumentContent.class);
  }

  @Nullable
  @Override
  public FileContent createFile(@Nullable Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    return (FileContent)create(project, file);
  }


  @NotNull
  @Override
  public DocumentContent createFragment(@Nullable Project project, @NotNull Document document, @NotNull TextRange range) {
    DocumentContent content = create(project, document);
    return createFragment(project, content, range);
  }

  @NotNull
  @Override
  public DocumentContent createFragment(@Nullable Project project, @NotNull DocumentContent content, @NotNull TextRange range) {
    return new DocumentFragmentContent(project, content, range);
  }


  @NotNull
  @Override
  public DiffContent createClipboardContent() {
    return createClipboardContent(null, null);
  }

  @NotNull
  @Override
  public DocumentContent createClipboardContent(@Nullable DocumentContent referent) {
    return createClipboardContent(null, referent);
  }

  @NotNull
  @Override
  public DiffContent createClipboardContent(@Nullable Project project) {
    return createClipboardContent(project, null);
  }

  @NotNull
  @Override
  public DocumentContent createClipboardContent(@Nullable Project project, @Nullable DocumentContent referent) {
    String text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);

    FileType type = referent != null ? referent.getContentType() : null;
    VirtualFile highlightFile = referent != null ? referent.getHighlightFile() : null;

    return createImpl(project, StringUtil.notNullize(text), type, "Clipboard.txt", highlightFile, false, false);
  }


  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     @NotNull byte[] content,
                                     @NotNull FilePath filePath) throws IOException {
    if (isBinaryContent(content, filePath.getFileType())) {
      return createBinaryImpl(project, content, filePath.getFileType(), filePath.getName(), filePath.getVirtualFile());
    }

    return createDocumentFromBytes(project, content, filePath);
  }

  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     @NotNull byte[] content,
                                     @NotNull FileType fileType,
                                     @NotNull String fileName) throws IOException {
    if (isBinaryContent(content, fileType)) {
      return createBinaryImpl(project, content, fileType, fileName, null);
    }

    return createDocumentFromBytes(project, content, fileType, fileName);
  }

  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     @NotNull byte[] content,
                                     @NotNull VirtualFile highlightFile) throws IOException {
    if (isBinaryContent(content, highlightFile.getFileType())) {
      return createBinaryImpl(project, content, highlightFile.getFileType(), highlightFile.getName(), highlightFile);
    }

    return createDocumentFromBytes(project, content, highlightFile);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                 @NotNull byte[] content,
                                                 @NotNull FileType fileType,
                                                 @NotNull String fileName) {
    Charset charset = guessCharset(project, content, fileType);
    return createFromBytesImpl(project, content, fileType, fileName, null, charset);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project, @NotNull byte[] content, @NotNull FilePath filePath) {
    Charset charset = guessCharset(content, filePath);
    return createFromBytesImpl(project, content, filePath.getFileType(), filePath.getName(), filePath.getVirtualFile(), charset);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project, @NotNull byte[] content, @NotNull VirtualFile highlightFile) {
    Charset charset = guessCharset(content, highlightFile);
    return createFromBytesImpl(project, content, highlightFile.getFileType(), highlightFile.getName(), highlightFile, charset);
  }

  @NotNull
  @Override
  public DiffContent createBinary(@Nullable Project project,
                                  @NotNull byte[] content,
                                  @NotNull FileType type,
                                  @NotNull String fileName) throws IOException {
    return createBinaryImpl(project, content, type, fileName, null);
  }


  @NotNull
  private static DiffContent createContentFromFile(@Nullable Project project,
                                                   @NotNull VirtualFile file) {
    return createContentFromFile(project, file, file);
  }

  @NotNull
  private static DiffContent createContentFromFile(@Nullable Project project,
                                                   @NotNull VirtualFile file,
                                                   @Nullable VirtualFile highlightFile) {
    if (file.isDirectory()) return new DirectoryContentImpl(project, file, highlightFile);

    Document document = ReadAction.compute(() -> {
      return FileDocumentManager.getInstance().getDocument(file);
    });
    if (document != null) {
      // TODO: add notification if file is decompiled ?
      return new FileDocumentContentImpl(project, document, file, highlightFile);
    }
    else {
      return new FileContentImpl(project, file, highlightFile);
    }
  }

  @NotNull
  private static DiffContent createBinaryImpl(@Nullable Project project,
                                              @NotNull byte[] content,
                                              @NotNull FileType type,
                                              @NotNull String fileName,
                                              @Nullable VirtualFile highlightFile) throws IOException {
    // workaround - our JarFileSystem and decompilers can't process non-local files
    boolean useTemporalFile = type instanceof ArchiveFileType || BinaryFileTypeDecompilers.INSTANCE.forFileType(type) != null;

    VirtualFile file;
    if (useTemporalFile) {
      file = createTemporalFile(project, "tmp", fileName, content);
    }
    else {
      file = new BinaryLightVirtualFile(fileName, type, content);
      file.setWritable(false);
    }
    file.putUserData(DiffUtil.TEMP_FILE_KEY, Boolean.TRUE);

    return createContentFromFile(project, file, highlightFile);
  }

  @NotNull
  private static DocumentContent createImpl(@Nullable Project project,
                                            @NotNull String text,
                                            @Nullable FileType fileType,
                                            @Nullable String fileName,
                                            @Nullable VirtualFile highlightFile,
                                            boolean respectLineSeparators,
                                            boolean readOnly) {
    return createImpl(project, text, fileType, fileName, highlightFile, null, null, respectLineSeparators, readOnly);
  }

  @NotNull
  private static DocumentContent createImpl(@Nullable Project project,
                                            @NotNull String text,
                                            @Nullable FileType fileType,
                                            @Nullable String fileName,
                                            @Nullable VirtualFile highlightFile,
                                            @Nullable Charset charset,
                                            @Nullable Boolean bom,
                                            boolean respectLineSeparators,
                                            boolean readOnly) {
    if (FileTypes.UNKNOWN.equals(fileType)) fileType = PlainTextFileType.INSTANCE;

    // TODO: detect invalid (different across the file) separators ?
    LineSeparator separator = respectLineSeparators ? StringUtil.detectSeparators(text) : null;
    String correctedContent = StringUtil.convertLineSeparators(text);

    Document document = createDocument(project, correctedContent, fileType, fileName, readOnly);
    DocumentContent content = new DocumentContentImpl(project, document, fileType, highlightFile, separator, charset, bom);

    if (fileName != null) content.putUserData(DiffUserDataKeysEx.FILE_NAME, fileName);

    return content;
  }

  @NotNull
  private static DocumentContent createFromBytesImpl(@Nullable Project project,
                                                     @NotNull byte[] content,
                                                     @NotNull FileType fileType,
                                                     @NotNull String fileName,
                                                     @Nullable VirtualFile highlightFile,
                                                     @NotNull Charset charset) {
    if (fileType.isBinary()) fileType = PlainTextFileType.INSTANCE;

    boolean isBOM = CharsetToolkit.guessFromBOM(content) != null;

    boolean malformedContent = false;
    String text = CharsetToolkit.tryDecodeString(content, charset);
    if (text == null) {
      text = CharsetToolkit.decodeString(content, charset);
      malformedContent = true;
    }

    DocumentContent documentContent = createImpl(project, text, fileType, fileName, highlightFile, charset, isBOM, true, true);

    if (malformedContent) {
      String notificationText = "Content was decoded with errors (using " + "'" + charset.name() + "' charset)";
      DiffUtil.addNotification(DiffNotifications.createNotification(notificationText, LightColors.RED), documentContent);
    }

    return documentContent;
  }


  @NotNull
  private static VirtualFile createTemporalFile(@Nullable Project project,
                                                @NotNull String prefix,
                                                @NotNull String suffix,
                                                @NotNull byte[] content) throws IOException {
    File tempFile = FileUtil.createTempFile(PathUtil.suggestFileName(prefix + "_", true, false),
                                            PathUtil.suggestFileName("_" + suffix, true, false), true);
    if (content.length != 0) {
      FileUtil.writeToFile(tempFile, content);
    }
    if (!tempFile.setWritable(false, false)) LOG.warn("Can't set writable attribute of temporal file");

    VirtualFile file = VfsUtil.findFileByIoFile(tempFile, true);
    if (file == null) {
      throw new IOException("Can't create temp file for revision content");
    }
    VfsUtil.markDirtyAndRefresh(true, true, true, file);
    return file;
  }

  @NotNull
  private static Document createDocument(@Nullable Project project,
                                         @NotNull String content,
                                         @Nullable FileType fileType,
                                         @Nullable String fileName,
                                         boolean readOnly) {
    if (project != null && !project.isDefault() &&
        fileType != null && !fileType.isBinary() &&
        Registry.is("diff.enable.psi.highlighting")) {
      if (fileName == null) {
        fileName = "diff." + StringUtil.defaultIfEmpty(fileType.getDefaultExtension(), "txt");
      }

      Document document = createPsiDocument(project, content, fileType, fileName, readOnly);
      if (document != null) return document;
    }

    Document document = EditorFactory.getInstance().createDocument(content);
    document.setReadOnly(readOnly);
    return document;
  }

  @Nullable
  private static Document createPsiDocument(@NotNull Project project,
                                            @NotNull String content,
                                            @NotNull FileType fileType,
                                            @NotNull String fileName,
                                            boolean readOnly) {
    return ReadAction.compute(() -> {
      LightVirtualFile file = new LightVirtualFile(fileName, fileType, content);
      file.setWritable(!readOnly);

      OutsidersPsiFileSupport.markFile(file);

      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null) return null;

      PsiDocumentManager.getInstance(project).getPsiFile(document);

      return document;
    });
  }

  private static boolean isBinaryContent(@NotNull byte[] content, @NotNull FileType fileType) {
    if (UnknownFileType.INSTANCE.equals(fileType)) {
      return guessCharsetFromContent(content) == null;
    }
    return fileType.isBinary();
  }


  @NotNull
  public static Charset guessCharset(@NotNull byte[] content, @NotNull FilePath filePath) {
    return guessCharset(content, filePath.getFileType(), filePath.getCharset());
  }

  @NotNull
  public static Charset guessCharset(@NotNull byte[] content, @NotNull VirtualFile highlightFile) {
    return guessCharset(content, highlightFile.getFileType(), highlightFile.getCharset());
  }

  @NotNull
  public static Charset guessCharset(@Nullable Project project, @NotNull byte[] content, @NotNull FileType fileType) {
    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : EncodingManager.getInstance();
    return guessCharset(content, fileType, e.getDefaultCharset());
  }


  @NotNull
  private static Charset guessCharset(@NotNull byte[] content, @NotNull FileType fileType, @NotNull Charset defaultCharset) {
    Charset bomCharset = CharsetToolkit.guessFromBOM(content);
    if (bomCharset != null) return bomCharset;

    if (fileType.isBinary()) {
      Charset guessedCharset = guessCharsetFromContent(content);
      if (guessedCharset != null) return guessedCharset;
    }

    return defaultCharset;
  }

  @Nullable
  private static Charset guessCharsetFromContent(@NotNull byte[] content) {
    // can't use CharsetToolkit.guessEncoding here because of false-positive INVALID_UTF8
    CharsetToolkit toolkit = new CharsetToolkit(content);

    Charset fromBOM = toolkit.guessFromBOM();
    if (fromBOM != null) return fromBOM;

    CharsetToolkit.GuessedEncoding guessedEncoding = toolkit.guessFromContent(content.length);
    switch (guessedEncoding) {
      case SEVEN_BIT:
        return Charset.forName("US-ASCII");
      case VALID_UTF8:
        return CharsetToolkit.UTF8_CHARSET;
      default:
        return null;
    }
  }
}
