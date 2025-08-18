// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.codeInsight.daemon.SyntheticPsiFileSupport;
import com.intellij.diff.actions.DocumentFragmentContent;
import com.intellij.diff.actions.ImmutableDocumentFragmentContent;
import com.intellij.diff.contents.*;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffNotificationProvider;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
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
import com.intellij.openapi.vfs.transformer.TextPresentationTransformer;
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.LightVirtualFileBase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.*;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@ApiStatus.Internal
public final class DiffContentFactoryImpl extends DiffContentFactoryEx {
  private static final Logger LOG = Logger.getInstance(DiffContentFactoryImpl.class);

  @Override
  public @NotNull EmptyContent createEmpty() {
    return new EmptyContent();
  }


  @Override
  public @NotNull DocumentContent create(@NotNull String text) {
    return create(null, text);
  }

  @Override
  public @NotNull DocumentContent create(@NotNull String text, @Nullable FileType type) {
    return create(null, text, type);
  }

  @Override
  public @NotNull DocumentContent create(@NotNull String text, @Nullable FileType type, boolean respectLineSeparators) {
    return create(null, text, type, respectLineSeparators);
  }

  @Override
  public @NotNull DocumentContent create(@NotNull String text, @Nullable VirtualFile highlightFile) {
    return create(null, text, highlightFile);
  }

  @Override
  public @NotNull DocumentContent create(@NotNull String text, @Nullable DocumentContent referent) {
    return create(null, text, referent);
  }


  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull String text) {
    return create(project, text, (FileType)null);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type) {
    return create(project, text, type, true);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FileType type, boolean respectLineSeparators) {
    return readOnlyDocumentContent(project)
      .contextByFileType(type)
      .buildFromText(text, respectLineSeparators);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FilePath filePath) {
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .buildFromText(text, true);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable VirtualFile highlightFile) {
    return readOnlyDocumentContent(project)
      .contextByHighlightFile(highlightFile)
      .buildFromText(text, true);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable DocumentContent referent) {
    if (referent == null) return create(text);
    return readOnlyDocumentContent(project)
      .contextByReferent(referent)
      .buildFromText(text, true);
  }


  @Override
  public @NotNull DocumentContent createEditable(@Nullable Project project, @NotNull String text, @Nullable FileType fileType) {
    return documentContent(project, false)
      .contextByFileType(fileType)
      .buildFromText(text, true);
  }

  @Override
  public @NotNull DocumentContent create(@NotNull Document document, @Nullable DocumentContent referent) {
    return create(null, document, referent);
  }


  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document) {
    return create(project, document, (FileType)null);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable FileType fileType) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) return new FileDocumentContentImpl(project, document, file);
    return new DocumentContentImpl(project, document, fileType);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable VirtualFile highlightFile) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && file.equals(highlightFile)) return new FileDocumentContentImpl(project, document, file);
    if (highlightFile == null) return new DocumentContentImpl(document);
    return new FileReferentDocumentContent(project, document, highlightFile);
  }

  @Override
  public @NotNull DocumentContent create(@Nullable Project project, @NotNull Document document, @Nullable DocumentContent referent) {
    if (referent == null) return new DocumentContentImpl(document);
    return new ContentReferentDocumentContent(project, document, referent);
  }


  @Override
  public @NotNull DiffContent create(@Nullable Project project, @NotNull VirtualFile file) {
    return createContentFromFile(project, file);
  }

  @Override
  public @NotNull DiffContent create(@Nullable Project project, @NotNull VirtualFile file, @Nullable VirtualFile highlightFile) {
    return createContentFromFile(project, file, highlightFile);
  }

  @Override
  public @Nullable DocumentContent createDocument(@Nullable Project project, final @NotNull VirtualFile file) {
    return ObjectUtils.tryCast(createContentFromFile(project, file), DocumentContent.class);
  }

  @Override
  public @Nullable FileContent createFile(@Nullable Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    return (FileContent)create(project, file);
  }


  @Override
  public @NotNull DocumentContent createFragment(@Nullable Project project, @NotNull Document document, @NotNull TextRange range) {
    DocumentContent content = create(project, document);
    return createFragment(project, content, range);
  }

  @Override
  public @NotNull DocumentContent createFragment(@Nullable Project project, @NotNull DocumentContent content, @NotNull TextRange range) {
    Document document = content.getDocument();
    if (document instanceof DocumentImpl && ((DocumentImpl)document).acceptsSlashR()) {
      LOG.warn("Create immutable fragment content - can't handle CR");
      return new ImmutableDocumentFragmentContent(content, range);
    }

    return new DocumentFragmentContent(project, content, range);
  }


  @Override
  public @NotNull DiffContent createClipboardContent() {
    return createClipboardContent(null, null);
  }

  @Override
  public @NotNull DocumentContent createClipboardContent(@Nullable DocumentContent referent) {
    return createClipboardContent(null, referent);
  }

  @Override
  public @NotNull DiffContent createClipboardContent(@Nullable Project project) {
    return createClipboardContent(project, null);
  }

  @Override
  public @NotNull DocumentContent createClipboardContent(@Nullable Project project, @Nullable DocumentContent referent) {
    String text = StringUtil.notNullize(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    return documentContent(project, false)
      .contextByReferent(referent)
      .withFileName("Clipboard.txt")
      .buildFromText(text, false);
  }


  @Override
  public @NotNull DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FilePath filePath) throws IOException {
    return createFromBytes(project, content, filePath, null);
  }

  @Override
  public @NotNull DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FilePath filePath,
                                              @Nullable Charset defaultCharset) throws IOException {
    if (defaultCharset == null && isBinaryContent(content, filePath.getFileType())) {
      return createBinaryImpl(project, content, filePath.getFileType(), filePath.getPath(), filePath.getVirtualFile());
    }

    return createDocumentFromBytes(project, content, filePath, defaultCharset);
  }

  @Override
  public @NotNull DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FileType fileType,
                                              @NotNull String fileName) throws IOException {
    if (isBinaryContent(content, fileType)) {
      return createBinary(project, content, fileType, fileName);
    }

    return createDocumentFromBytes(project, content, fileType, fileName);
  }

  @Override
  public @NotNull DiffContent createFromBytes(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull VirtualFile highlightFile) throws IOException {
    if (isBinaryContent(content, highlightFile.getFileType())) {
      return createBinaryImpl(project, content, highlightFile.getFileType(), highlightFile.getPath(), highlightFile);
    }

    return createDocumentFromBytes(project, content, highlightFile);
  }

  @Override
  public @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                          byte @NotNull [] content,
                                                          @NotNull FileType fileType,
                                                          @NotNull String fileName) {
    return readOnlyDocumentContent(project)
      .contextByFileType(fileType)
      .withFileName(fileName)
      .buildFromBytes(content);
  }

  @Override
  public @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project, byte @NotNull [] content, @NotNull FilePath filePath) {
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .buildFromBytes(content);
  }

  private @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                           byte @NotNull [] content,
                                                           @NotNull FilePath filePath,
                                                           @Nullable Charset defaultCharset) {
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .withDefaultCharset(defaultCharset)
      .buildFromBytes(content);
  }

  @Override
  public @NotNull DocumentContent createDocumentFromBytes(@Nullable Project project, byte @NotNull [] content, @NotNull VirtualFile highlightFile) {
    return readOnlyDocumentContent(project)
      .contextByHighlightFile(highlightFile)
      .buildFromBytes(content);
  }

  @Override
  public @NotNull DiffContent createBinary(@Nullable Project project,
                                           byte @NotNull [] content,
                                           @NotNull FileType type,
                                           @NotNull String fileName) throws IOException {
    return createBinaryImpl(project, content, type, fileName, null);
  }


  private static @NotNull DiffContent createContentFromFile(@Nullable Project project,
                                                            @NotNull VirtualFile file) {
    return createContentFromFile(project, file, file);
  }

  private static @NotNull DiffContent createContentFromFile(@Nullable Project project,
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

  private static @NotNull DiffContent createBinaryImpl(@Nullable Project project,
                                                       byte @NotNull [] content,
                                                       @NotNull FileType type,
                                                       @NotNull @NonNls @SystemIndependent String lightFilePath,
                                                       @Nullable VirtualFile highlightFile) throws IOException {
    // workaround - our JarFileSystem and decompilers can't process non-local files
    boolean useTemporalFile = type instanceof ArchiveFileType || BinaryFileTypeDecompilers.getInstance().forFileType(type) != null;

    VirtualFile file;
    if (useTemporalFile) {
      file = createTemporalFile(PathUtil.getFileName(lightFilePath), content);
    }
    else {
      file = new MyBinaryLightVirtualFile(lightFilePath, type, content);
      file.setWritable(false);
    }
    file.putUserData(DiffUtil.TEMP_FILE_KEY, Boolean.TRUE);

    return createContentFromFile(project, file, highlightFile);
  }


  private static @NotNull VirtualFile createTemporalFile(@NonNls @NotNull String suffix, byte @NotNull [] content) throws IOException {
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

  private static @NotNull Document createDocument(@Nullable Project project,
                                                  @NotNull String content,
                                                  @Nullable FileType fileType,
                                                  @NotNull @NonNls @SystemIndependent String lightFilePath,
                                                  boolean readOnly) {
    Document document = createPsiDocument(project, content, fileType, lightFilePath, readOnly);
    if (document == null) {
      document = EditorFactory.getInstance().createDocument(content);
      document.setReadOnly(readOnly);
    }
    return document;
  }

  private static @Nullable Document createPsiDocument(@Nullable Project project,
                                                      @NotNull String content,
                                                      @Nullable FileType fileType,
                                                      @NotNull @NonNls @SystemIndependent String lightFilePath,
                                                      boolean readOnly) {
    if (project == null || project.isDefault()) return null;
    if (fileType != null && fileType.isBinary()) return null;

    LightVirtualFile file = new MyLightVirtualFile(lightFilePath, fileType, content);

    TextPresentationTransformers transformersManager = ApplicationManager.getApplication().getService(TextPresentationTransformers.class);
    TextPresentationTransformer transformer = transformersManager.forFileType(file.getFileType());
    //noinspection ConstantValue
    if (transformer != null) {
      String convertedText = transformer.fromPersistent(content, file).toString();
      file = new MyLightVirtualFile(lightFilePath, fileType, convertedText);
    }

    file.setWritable(!readOnly);

    LightVirtualFile finalFile = file;
    return ReadAction.compute(() -> {
      Document document = FileDocumentManager.getInstance().getDocument(finalFile, project);
      if (document == null) {
        return null;
      }

      PsiDocumentManager.getInstance(project).getPsiFile(document);
      return document;
    });
  }

  @ApiStatus.Internal
  public static boolean isBinaryContent(byte @NotNull [] content, @NotNull FileType fileType) {
    if (fileType instanceof UIBasedFileType) {
      return true; // text file, that should be shown as binary (SVG, UI Forms).
    }

    if (!fileType.isBinary()) {
      return false;
    }

    if (UnknownFileType.INSTANCE.equals(fileType) ||
        fileType instanceof INativeFileType ||
        Registry.is("diff.use.aggressive.text.file.detection")) {
      return guessCharsetFromContent(content) == null;
    }
    return true;
  }


  public static @NotNull Charset guessCharset(@Nullable Project project, byte @NotNull [] content, @NotNull FilePath filePath) {
    return guessCharset(project, content, filePath.getFileType(), filePath.getPath(), filePath.getVirtualFile(), null);
  }

  private static @NotNull Charset guessCharset(@Nullable Project project,
                                               byte @NotNull [] content,
                                               @Nullable FileType fileType,
                                               @NotNull @NonNls @SystemIndependent String lightFilePath,
                                               @Nullable VirtualFile highlightFile,
                                               @Nullable Charset defaultCharset) {
    Charset bomCharset = CharsetToolkit.guessFromBOM(content);
    if (bomCharset != null) return bomCharset;

    if (defaultCharset != null) return defaultCharset;

    if (fileType != null && !fileType.isBinary()) {
      Charset fileTypeCharset = guessFileTypeCharset(project, content, fileType, lightFilePath);
      if (fileTypeCharset != null) return fileTypeCharset;
    }

    if (highlightFile != null) {
      Charset fileCharset = highlightFile.getCharset();
      return takeCharsetOrGuessUTF(fileCharset, content);
    }
    else {
      EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : EncodingManager.getInstance();
      Charset globalCharset = e.getDefaultCharset();
      return takeCharsetOrGuessUTF(globalCharset, content);
    }
  }

  private static @NotNull Charset takeCharsetOrGuessUTF(@NotNull Charset charset, byte @NotNull [] content) {
    if (StandardCharsets.UTF_8.equals(charset)) {
      // GuessedEncoding can't detect UTF8-incompatible charsets, verification can be skipped
      return charset;
    }

    if (CharsetToolkit.tryDecodeString(content, charset) != null) {
      // charset is OK
      return charset;
    }

    Charset charsetFromContent = guessCharsetFromContent(content);
    if (charsetFromContent != null) {
      return charsetFromContent;
    }

    // charset is NOT OK, but we don't know better
    return charset;
  }

  private static @Nullable Charset guessCharsetFromContent(byte @NotNull [] content) {
    if (content.length == 0) return null;
    // can't use CharsetToolkit.guessEncoding here because of false-positive INVALID_UTF8
    CharsetToolkit toolkit = new CharsetToolkit(content, Charset.defaultCharset(), false);

    Charset fromBOM = toolkit.guessFromBOM();
    if (fromBOM != null) return fromBOM;

    CharsetToolkit.GuessedEncoding guessedEncoding = toolkit.guessFromContent(content.length);
    return switch (guessedEncoding) {
      case SEVEN_BIT -> StandardCharsets.US_ASCII;
      case VALID_UTF8 -> StandardCharsets.UTF_8;
      default -> null;
    };
  }

  private static @Nullable Charset guessFileTypeCharset(@Nullable Project project,
                                                        byte @NotNull [] content,
                                                        @NotNull FileType fileType,
                                                        @NotNull @NonNls @SystemIndependent String lightFilePath) {
    LightVirtualFileBase file = new MyBinaryLightVirtualFile(lightFilePath, fileType, content);
    if (project != null) {
      // pass Project to the EncodingManagerImpl to delegate to the EncodingProjectManagerImpl when needed
      try (AccessToken ignored = ProjectLocator.withPreferredProject(file, project)) {
        return CharsetToolkit.forName(fileType.getCharset(file, content));
      }
    }
    else {
      return CharsetToolkit.forName(fileType.getCharset(file, content));
    }
  }

  private @NotNull DocumentContentBuilder readOnlyDocumentContent(@Nullable Project project) {
    return documentContent(project, true);
  }

  @Override
  public @NotNull DocumentContentBuilder documentContent(@Nullable Project project, boolean readOnly) {
    return new DocumentContentBuilderImpl(project).withReadOnly(readOnly);
  }

  private static class DocumentContentBuilderImpl implements DocumentContentBuilder {
    private final @Nullable Project project;
    private boolean readOnly;

    private @Nullable Context context;
    private @Nullable @SystemIndependent String originalFilePath;
    private @Nullable @NonNls String fileName;
    private @Nullable @NonNls Charset defaultCharset;

    DocumentContentBuilderImpl(@Nullable Project project) {
      this.project = project;
    }

    public @NotNull DocumentContentBuilder withReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder withFileName(@Nullable String fileName) {
      this.fileName = fileName;
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder withDefaultCharset(@Nullable Charset charset) {
      this.defaultCharset = charset;
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder contextByFileType(@Nullable FileType fileType) {
      if (fileType != null) {
        context = new Context.ByFileType(fileType);
      }
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder contextByFilePath(@Nullable FilePath filePath) {
      if (filePath != null) {
        context = new Context.ByFilePath(filePath);
        originalFilePath = filePath.getPath();
        fileName = filePath.getName();
      }
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder contextByHighlightFile(@Nullable VirtualFile file) {
      if (file != null) {
        context = new Context.ByHighlightFile(file);
        originalFilePath = file.getPath();
        fileName = file.getName();
      }
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder contextByReferent(@Nullable DocumentContent referent) {
      if (referent != null) {
        context = new Context.ByReferent(referent);
        VirtualFile file = referent.getHighlightFile();
        if (file != null) {
          originalFilePath = file.getPath();
          fileName = file.getName();
        }
      }
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder contextByProvider(@Nullable ContextProvider contextProvider) {
      if (contextProvider != null) {
        contextProvider.passContext(this);
      }
      return this;
    }

    @Override
    public @NotNull DocumentContent buildFromText(@NotNull String text, boolean respectLineSeparators) {
      FileType fileType = guessFileType();
      String lightFilePath = constructLightFilePath();

      TextContent textContent = TextContent.fromText(text, respectLineSeparators);

      Document document = createDocument(project, textContent.text, fileType, lightFilePath, readOnly);
      return build(document, textContent);
    }

    @Override
    public @NotNull DocumentContent buildFromBytes(byte @NotNull [] content) {
      FileType fileType = guessFileType();
      String lightFilePath = constructLightFilePath();
      VirtualFile highlightFile = constructHighlightFile();
      Charset charset = guessCharset(project, content, fileType, lightFilePath, highlightFile, defaultCharset);

      // decode bytes ourselves to detect incorrect charsets
      TextContent textContent = TextContent.fromBytes(content, charset);

      Document document = createDocument(project, textContent.text, fileType, lightFilePath, readOnly);
      return build(document, textContent);
    }

    private @NotNull DocumentContent build(@NotNull Document document, @NotNull TextContent textContent) {
      DocumentContent documentContent = new ContextReferentDocumentContent(project, document, textContent, context);

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file != null && !file.isInLocalFileSystem()) {
        SyntheticPsiFileSupport.markFile(file, originalFilePath);
      }

      if (fileName != null) {
        documentContent.putUserData(DiffUserDataKeysEx.FILE_NAME, fileName);
      }

      if (textContent.notification != null) {
        DiffUtil.addNotification(textContent.notification, documentContent);
      }

      return documentContent;
    }

    private @Nullable FileType guessFileType() {
      return context != null ? context.guessContentType() : null;
    }

    private @NotNull @NonNls String constructLightFilePath() {
      if (originalFilePath != null) {
        return originalFilePath;
      }

      String name = fileName;
      if (name == null) {
        FileType fileType = guessFileType();
        name = "diff." + StringUtil.defaultIfEmpty(fileType != null ? fileType.getDefaultExtension() : null, "txt");
      }
      return name;
    }

    private @Nullable VirtualFile constructHighlightFile() {
      return context != null ? context.getHighlightFile() : null;
    }
  }

  private static final class MyLightVirtualFile extends LightVirtualFile implements DiffLightVirtualFile {
    private final String myPath;

    private MyLightVirtualFile(@NotNull @NonNls @SystemIndependent String path, @Nullable FileType fileType, @NotNull String content) {
      super(PathUtil.getFileName(path), fileType, content);
      myPath = path;
    }

    @Override
    public @NotNull String getPath() {
      return myPath;
    }

    @Override
    public String toString() {
      return "DiffContentFactory " + super.toString();
    }
  }

  private static class MyBinaryLightVirtualFile extends BinaryLightVirtualFile implements DiffLightVirtualFile {
    private final String myPath;

    MyBinaryLightVirtualFile(@NotNull @NonNls @SystemIndependent String path, @Nullable FileType type, byte @NotNull [] content) {
      super(PathUtil.getFileName(path), type, content);
      myPath = path;
    }

    @Override
    public @NotNull String getPath() {
      return myPath;
    }

    @Override
    public String toString() {
      return "DiffContentFactory " + super.toString();
    }
  }

  private static final class FileReferentDocumentContent extends DocumentContentBase {
    private final @NotNull VirtualFile myHighlightFile;

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

  private static final class ContentReferentDocumentContent extends DocumentContentBase {
    private final @NotNull DocumentContent myReferent;

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

  private static final class ContextReferentDocumentContent extends DocumentContentBase {
    private final @Nullable LineSeparator mySeparator;
    private final @Nullable Charset myCharset;
    private final @Nullable Boolean myBOM;

    private final @Nullable Context myReferent;

    private ContextReferentDocumentContent(@Nullable Project project, @NotNull Document document,
                                           @NotNull TextContent content, @Nullable Context referent) {
      super(project, document);
      mySeparator = content.separators;
      myCharset = content.charset;
      myBOM = content.isBom;

      myReferent = referent;
    }

    @Override
    public @Nullable FileType getContentType() {
      VirtualFile file = FileDocumentManager.getInstance().getFile(getDocument());
      if (file != null) return file.getFileType();
      if (myReferent != null) return myReferent.guessContentType();
      return null;
    }

    @Override
    public @Nullable VirtualFile getHighlightFile() {
      return myReferent != null ? myReferent.getHighlightFile() : null;
    }

    @Override
    public @Nullable LineSeparator getLineSeparator() {
      return mySeparator;
    }

    @Override
    public @Nullable Charset getCharset() {
      return myCharset;
    }

    @Override
    public @Nullable Boolean hasBom() {
      return myBOM;
    }
  }

  private static class TextContent {
    public final @NotNull String text;
    public @Nullable LineSeparator separators;
    public @Nullable Charset charset;
    public @Nullable Boolean isBom;
    public @Nullable DiffNotificationProvider notification;

    TextContent(@NotNull String text) {
      this.text = text;
    }

    public static @NotNull TextContent fromText(@NotNull String text, boolean respectLineSeparators) {
      // TODO: detect invalid (different across the file) separators ?
      String correctedContent = StringUtil.convertLineSeparators(text);

      TextContent textContent = new TextContent(correctedContent);
      if (respectLineSeparators) {
        textContent.separators = StringUtil.detectSeparators(text);
      }
      return textContent;
    }

    public static @NotNull TextContent fromBytes(byte @NotNull [] content, @NotNull Charset charset) {
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
        textContent.notification =
          DiffNotifications.createNotificationProvider(notificationText, LightColors.RED, EditorNotificationPanel.Status.Error);
      }
      return textContent;
    }
  }

  private interface Context {
    @Nullable VirtualFile getHighlightFile();

    @Nullable FileType guessContentType();

    class ByHighlightFile implements Context {
      private final VirtualFile myHighlightFile;

      public ByHighlightFile(@NotNull VirtualFile highlightFile) {
        myHighlightFile = highlightFile;
      }

      @Override
      public @Nullable VirtualFile getHighlightFile() {
        return myHighlightFile;
      }

      @Override
      public @Nullable FileType guessContentType() {
        return myHighlightFile.getFileType();
      }
    }

    class ByReferent implements Context {
      private final DocumentContent myReferent;

      public ByReferent(@NotNull DocumentContent referent) {
        myReferent = referent;
      }

      @Override
      public @Nullable VirtualFile getHighlightFile() {
        return myReferent.getHighlightFile();
      }

      @Override
      public @Nullable FileType guessContentType() {
        return myReferent.getContentType();
      }
    }

    class ByFilePath implements Context {
      private final FilePath myFilePath;

      public ByFilePath(@NotNull FilePath filePath) {
        myFilePath = filePath;
      }

      @Override
      public @Nullable VirtualFile getHighlightFile() {
        return myFilePath.getVirtualFile();
      }

      @Override
      public @Nullable FileType guessContentType() {
        VirtualFile file = myFilePath.getVirtualFile();
        if (file == null) {
          return FileTypeManager.getInstance().getFileTypeByFileName(myFilePath.getName());
        }
        return file.getFileType();
      }
    }

    class ByFileType implements Context {
      private final FileType myFileType;

      public ByFileType(@NotNull FileType fileType) {
        myFileType = fileType;
      }

      @Override
      public @Nullable VirtualFile getHighlightFile() {
        return null;
      }

      @Override
      public @Nullable FileType guessContentType() {
        return myFileType;
      }
    }
  }
}
