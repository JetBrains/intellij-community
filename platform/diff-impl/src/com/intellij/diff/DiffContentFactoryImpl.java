// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
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
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    return readOnlyDocumentContent(project)
      .contextByFileType(type)
      .buildFromText(text, respectLineSeparators);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @NotNull FilePath filePath) {
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .buildFromText(text, true);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable VirtualFile highlightFile) {
    return readOnlyDocumentContent(project)
      .contextByHighlightFile(highlightFile)
      .buildFromText(text, true);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable DocumentContent referent) {
    if (referent == null) return create(text);
    return readOnlyDocumentContent(project)
      .contextByReferent(referent)
      .buildFromText(text, false);
  }


  @NotNull
  @Override
  public DocumentContent createEditable(@Nullable Project project, @NotNull String text, @Nullable FileType fileType) {
    return mutableDocumentContent(project)
      .contextByFileType(fileType)
      .buildFromText(text, false);
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
    return new DocumentContentImpl(project, document, fileType);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable VirtualFile highlightFile) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && file.equals(highlightFile)) return new FileDocumentContentImpl(project, document, file);
    if (highlightFile == null) return new DocumentContentImpl(document);
    return new FileReferentDocumentContent(project, document, highlightFile);
  }

  @NotNull
  @Override
  public DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable DocumentContent referent) {
    if (referent == null) return new DocumentContentImpl(document);
    return new ContentReferentDocumentContent(project, document, referent);
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
    String text = StringUtil.notNullize(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));

    return mutableDocumentContent(project)
      .contextByReferent(referent)
      .withFileName("Clipboard.txt")
      .buildFromText(text, false);
  }


  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     byte @NotNull [] content,
                                     @NotNull FilePath filePath) throws IOException {
    return createFromBytes(project, content, filePath, null);
  }

  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     byte @NotNull [] content,
                                     @NotNull FilePath filePath,
                                     @Nullable Charset defaultCharset) throws IOException {
    if (defaultCharset == null && isBinaryContent(content, filePath.getFileType())) {
      return createBinaryImpl(project, content, filePath.getFileType(), filePath, filePath.getVirtualFile());
    }

    return createDocumentFromBytes(project, content, filePath, defaultCharset);
  }

  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     byte @NotNull [] content,
                                     @NotNull FileType fileType,
                                     @NotNull String fileName) throws IOException {
    if (isBinaryContent(content, fileType)) {
      return createBinaryImpl(project, content, fileType, VcsUtil.getFilePath(fileName), null);
    }

    return createDocumentFromBytes(project, content, fileType, fileName);
  }

  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     byte @NotNull [] content,
                                     @NotNull VirtualFile highlightFile) throws IOException {
    if (isBinaryContent(content, highlightFile.getFileType())) {
      return createBinaryImpl(project, content, highlightFile.getFileType(), VcsUtil.getFilePath(highlightFile), highlightFile);
    }

    return createDocumentFromBytes(project, content, highlightFile);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                 byte @NotNull [] content,
                                                 @NotNull FileType fileType,
                                                 @NotNull String fileName) {
    Charset charset = guessCharset(project, content, fileType);
    return readOnlyDocumentContent(project)
      .contextByFileType(fileType)
      .withFileName(fileName)
      .buildFromBytes(content, charset);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project, byte @NotNull [] content, @NotNull FilePath filePath) {
    Charset charset = guessCharset(project, content, filePath);
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .buildFromBytes(content, charset);
  }

  @NotNull
  private static DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                         byte @NotNull [] content,
                                                         @NotNull FilePath filePath,
                                                         @Nullable Charset defaultCharset) {
    Charset charset = guessCharset(project, content, filePath, defaultCharset);
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .buildFromBytes(content, charset);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project, byte @NotNull [] content, @NotNull VirtualFile highlightFile) {
    Charset charset = guessCharset(project, content, highlightFile);
    return readOnlyDocumentContent(project)
      .contextByHighlightFile(highlightFile)
      .buildFromBytes(content, charset);
  }

  @NotNull
  @Override
  public DiffContent createBinary(@Nullable Project project,
                                  byte @NotNull [] content,
                                  @NotNull FileType type,
                                  @NotNull String fileName) throws IOException {
    return createBinaryImpl(project, content, type, VcsUtil.getFilePath(fileName), null);
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

    Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
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
                                              byte @NotNull [] content,
                                              @NotNull FileType type,
                                              @NotNull FilePath path,
                                              @Nullable VirtualFile highlightFile) throws IOException {
    // workaround - our JarFileSystem and decompilers can't process non-local files
    boolean useTemporalFile = type instanceof ArchiveFileType || BinaryFileTypeDecompilers.getInstance().forFileType(type) != null;

    VirtualFile file;
    if (useTemporalFile) {
      file = createTemporalFile(path.getName(), content);
    }
    else {
      file = new MyBinaryLightVirtualFile(path, type, content);
      file.setWritable(false);
    }
    file.putUserData(DiffUtil.TEMP_FILE_KEY, Boolean.TRUE);

    return createContentFromFile(project, file, highlightFile);
  }


  @NotNull
  private static VirtualFile createTemporalFile(@NonNls @NotNull String suffix, byte @NotNull [] content) throws IOException {
    File tempFile = FileUtil.createTempFile("tmp_", PathUtil.suggestFileName("_" + suffix, true, false), true);
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
                                         @Nullable @NonNls String fileName,
                                         boolean readOnly) {
    Document document = createPsiDocument(project, content, fileType, fileName, readOnly);
    if (document == null) {
      document = EditorFactory.getInstance().createDocument(content);
      document.setReadOnly(readOnly);
    }
    return document;
  }

  @Nullable
  private static Document createPsiDocument(@Nullable Project project,
                                            @NotNull String content,
                                            @Nullable FileType fileType,
                                            @NonNls @Nullable String fileName,
                                            boolean readOnly) {
    if (project == null || project.isDefault()) return null;
    if (fileType == null || fileType.isBinary()) return null;

    if (fileName == null) {
      fileName = "diff." + StringUtil.defaultIfEmpty(fileType.getDefaultExtension(), "txt");
    }

    LightVirtualFile file = new LightVirtualFile(fileName, fileType, content);
    file.setWritable(!readOnly);

    Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    if (document == null) return null;

    ReadAction.run(() -> PsiDocumentManager.getInstance(project).getPsiFile(document));

    return document;
  }

  private static boolean isBinaryContent(byte @NotNull [] content, @NotNull FileType fileType) {
    if (UnknownFileType.INSTANCE.equals(fileType)) {
      return guessCharsetFromContent(content) == null;
    }
    if (fileType instanceof UIBasedFileType) {
      return true;
    }
    return fileType.isBinary();
  }


  @NotNull
  public static Charset guessCharset(@Nullable Project project, byte @NotNull [] content, @NotNull FilePath filePath) {
    return guessCharset(project, content, filePath, null);
  }

  @NotNull
  private static Charset guessCharset(@Nullable Project project,
                                      byte @NotNull [] content,
                                      @NotNull FilePath filePath,
                                      @Nullable Charset defaultCharset) {
    FileType fileType = filePath.getFileType();
    Charset charset = guessCharsetFromContent(content, fileType);
    if (charset != null) return charset;

    if (defaultCharset != null) return defaultCharset;

    if (fileType == StdFileTypes.PROPERTIES) {
      EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : EncodingManager.getInstance();
      Charset propertiesCharset = e.getDefaultCharsetForPropertiesFiles(null);
      if (propertiesCharset != null) return propertiesCharset;
    }

    return filePath.getCharset(project);
  }

  @NotNull
  public static Charset guessCharset(@Nullable Project project, byte @NotNull [] content, @NotNull VirtualFile highlightFile) {
    Charset charset = guessCharsetFromContent(content, highlightFile.getFileType());
    if (charset != null) return charset;

    return highlightFile.getCharset();
  }

  @NotNull
  public static Charset guessCharset(@Nullable Project project, byte @NotNull [] content, @NotNull FileType fileType) {
    Charset charset = guessCharsetFromContent(content, fileType);
    if (charset != null) return charset;

    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : EncodingManager.getInstance();
    return e.getDefaultCharset();
  }


  @Nullable
  private static Charset guessCharsetFromContent(byte @NotNull [] content, @NotNull FileType fileType) {
    Charset bomCharset = CharsetToolkit.guessFromBOM(content);
    if (bomCharset != null) return bomCharset;

    if (fileType.isBinary()) {
      Charset guessedCharset = guessCharsetFromContent(content);
      if (guessedCharset != null) return guessedCharset;
    }

    return null;
  }

  @Nullable
  private static Charset guessCharsetFromContent(byte @NotNull [] content) {
    // can't use CharsetToolkit.guessEncoding here because of false-positive INVALID_UTF8
    CharsetToolkit toolkit = new CharsetToolkit(content);

    Charset fromBOM = toolkit.guessFromBOM();
    if (fromBOM != null) return fromBOM;

    CharsetToolkit.GuessedEncoding guessedEncoding = toolkit.guessFromContent(content.length);
    switch (guessedEncoding) {
      case SEVEN_BIT:
        return StandardCharsets.US_ASCII;
      case VALID_UTF8:
        return StandardCharsets.UTF_8;
      default:
        return null;
    }
  }

  @NotNull
  public static DocumentContentBuilder readOnlyDocumentContent(@Nullable Project project) {
    return new DocumentContentBuilder(project).withReadOnly(true);
  }

  @NotNull
  public static DocumentContentBuilder mutableDocumentContent(@Nullable Project project) {
    return new DocumentContentBuilder(project);
  }

  private static class DocumentContentBuilder {
    private final @Nullable Project project;
    private boolean readOnly;

    private @Nullable VirtualFile highlightFile;
    private @Nullable FileType fileType;
    private @Nullable FilePath originalFilePath;
    private @Nullable @NonNls String fileName;

    DocumentContentBuilder(@Nullable Project project) {
      this.project = project;
    }

    @NotNull
    public DocumentContentBuilder withReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    @NotNull
    public DocumentContentBuilder withFileName(@Nullable String fileName) {
      this.fileName = fileName;
      return this;
    }

    @NotNull
    public DocumentContentBuilder contextByFileType(@Nullable FileType fileType) {
      this.fileType = fileType;
      return this;
    }

    @NotNull
    public DocumentContentBuilder contextByFilePath(@Nullable FilePath filePath) {
      if (filePath != null) {
        fileType = filePath.getFileType();
        highlightFile = filePath.getVirtualFile();
        originalFilePath = filePath;
        fileName = filePath.getName();
      }
      return this;
    }

    @NotNull
    public DocumentContentBuilder contextByHighlightFile(@Nullable VirtualFile file) {
      if (file != null) {
        fileType = file.getFileType();
        highlightFile = file;
        originalFilePath = VcsUtil.getFilePath(file);
        fileName = file.getName();
      }
      return this;
    }

    @NotNull
    public DocumentContentBuilder contextByReferent(@Nullable DocumentContent referent) {
      if (referent != null) {
        fileType = referent.getContentType();
        highlightFile = referent.getHighlightFile();
        if (highlightFile != null) {
          originalFilePath = VcsUtil.getFilePath(highlightFile);
          fileName = highlightFile.getName();
        }
      }
      return this;
    }

    @NotNull
    public DocumentContent buildFromText(@NotNull String text, boolean respectLineSeparators) {
      return build(TextContent.fromText(text, respectLineSeparators));
    }

    @NotNull
    public DocumentContent buildFromBytes(byte @NotNull [] content, @NotNull Charset charset) {
      return build(TextContent.fromBytes(content, charset));
    }

    @NotNull
    public DocumentContent build(@NotNull TextContent textContent) {
      Document document = createDocument(project, textContent.text, fileType, fileName, readOnly);

      DocumentContent documentContent = new DocumentContentImpl(project, document, fileType, highlightFile,
                                                                textContent.separators, textContent.charset, textContent.isBom);

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (originalFilePath != null && file instanceof LightVirtualFile) {
        OutsidersPsiFileSupport.markFile(file, originalFilePath.getPath());
      }

      if (fileName != null) {
        documentContent.putUserData(DiffUserDataKeysEx.FILE_NAME, fileName);
      }

      if (textContent.notification != null) {
        DiffUtil.addNotification(textContent.notification, documentContent);
      }

      return documentContent;
    }
  }

  private static class MyBinaryLightVirtualFile extends BinaryLightVirtualFile {
    private final FilePath myPath;

    MyBinaryLightVirtualFile(@NotNull FilePath path, FileType type, byte @NotNull [] content) {
      super(path.getName(), type, content);
      myPath = path;
    }

    @NotNull
    @Override
    public String getPath() {
      return myPath.getPath();
    }
  }

  private static class FileReferentDocumentContent extends DocumentContentBase {
    @NotNull private final VirtualFile myHighlightFile;

    private FileReferentDocumentContent(@Nullable Project project, @NotNull Document document, @NotNull VirtualFile highlightFile) {
      super(project, document);
      myHighlightFile = highlightFile;
    }

    @Override
    public @NotNull FileType getContentType() {
      return myHighlightFile.getFileType();
    }

    @Override
    public @NotNull VirtualFile getHighlightFile() {
      return myHighlightFile;
    }
  }

  private static class ContentReferentDocumentContent extends DocumentContentBase {
    @NotNull private final DocumentContent myReferent;

    private ContentReferentDocumentContent(@Nullable Project project, @NotNull Document document, @NotNull DocumentContent referent) {
      super(project, document);
      myReferent = referent;
    }

    @Override
    public @Nullable FileType getContentType() {
      return myReferent.getContentType();
    }

    @Override
    public @Nullable VirtualFile getHighlightFile() {
      return myReferent.getHighlightFile();
    }
  }

  private static class TextContent {
    public final @NotNull String text;
    public @Nullable LineSeparator separators;
    public @Nullable Charset charset;
    public @Nullable Boolean isBom;
    public @Nullable JComponent notification;

    TextContent(@NotNull String text) {
      this.text = text;
    }

    @NotNull
    public static TextContent fromText(@NotNull String text, boolean respectLineSeparators) {
      // TODO: detect invalid (different across the file) separators ?
      String correctedContent = StringUtil.convertLineSeparators(text);

      TextContent textContent = new TextContent(correctedContent);
      if (respectLineSeparators) {
        textContent.separators = StringUtil.detectSeparators(text);
      }
      return textContent;
    }

    @NotNull
    public static TextContent fromBytes(byte @NotNull [] content, @NotNull Charset charset) {
      boolean isBom = CharsetToolkit.guessFromBOM(content) != null;

      boolean malformedContent = false;
      String text = CharsetToolkit.tryDecodeString(content, charset);
      if (text == null) {
        text = CharsetToolkit.decodeString(content, charset);
        malformedContent = true;
      }

      TextContent textContent = fromText(text, true);
      textContent.charset = charset;
      textContent.isBom = isBom;
      if (malformedContent) {
        String notificationText = DiffBundle.message("error.content.decoded.with.wrong.charset", charset.name());
        textContent.notification = DiffNotifications.createNotification(notificationText, LightColors.RED);
      }
      return textContent;
    }
  }
}
