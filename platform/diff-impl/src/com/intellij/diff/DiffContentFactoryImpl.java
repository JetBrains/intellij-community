// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.diff.actions.DocumentFragmentContent;
import com.intellij.diff.actions.ImmutableDocumentFragmentContent;
import com.intellij.diff.contents.*;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffNotificationProvider;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.vcs.DiffVcsFacade;
import com.intellij.ide.highlighter.ArchiveFileType;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.LightVirtualFileBase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public DocumentContent create(@Nullable Project project, @NotNull String text, @Nullable FilePath filePath) {
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
      .buildFromText(text, true);
  }


  @NotNull
  @Override
  public DocumentContent createEditable(@Nullable Project project, @NotNull String text, @Nullable FileType fileType) {
    return documentContent(project, false)
      .contextByFileType(fileType)
      .buildFromText(text, true);
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

  @NotNull
  @Override
  public DiffContent create(@Nullable Project project, @NotNull VirtualFile file, @Nullable VirtualFile highlightFile) {
    return createContentFromFile(project, file, highlightFile);
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
    Document document = content.getDocument();
    if (document instanceof DocumentImpl && ((DocumentImpl)document).acceptsSlashR()) {
      LOG.warn("Create immutable fragment content - can't handle CR");
      return new ImmutableDocumentFragmentContent(content, range);
    }

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
    return documentContent(project, false)
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
      return createBinaryImpl(project, content, fileType, DiffVcsFacade.getInstance().getFilePath(fileName), null);
    }

    return createDocumentFromBytes(project, content, fileType, fileName);
  }

  @NotNull
  @Override
  public DiffContent createFromBytes(@Nullable Project project,
                                     byte @NotNull [] content,
                                     @NotNull VirtualFile highlightFile) throws IOException {
    if (isBinaryContent(content, highlightFile.getFileType())) {
      FilePath filePath = DiffVcsFacade.getInstance().getFilePath(highlightFile);
      return createBinaryImpl(project, content, highlightFile.getFileType(), filePath, highlightFile);
    }

    return createDocumentFromBytes(project, content, highlightFile);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                 byte @NotNull [] content,
                                                 @NotNull FileType fileType,
                                                 @NotNull String fileName) {
    return readOnlyDocumentContent(project)
      .contextByFileType(fileType)
      .withFileName(fileName)
      .buildFromBytes(content);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project, byte @NotNull [] content, @NotNull FilePath filePath) {
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .buildFromBytes(content);
  }

  @NotNull
  private DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                  byte @NotNull [] content,
                                                  @NotNull FilePath filePath,
                                                  @Nullable Charset defaultCharset) {
    return readOnlyDocumentContent(project)
      .contextByFilePath(filePath)
      .withDefaultCharset(defaultCharset)
      .buildFromBytes(content);
  }

  @NotNull
  @Override
  public DocumentContent createDocumentFromBytes(@Nullable Project project, byte @NotNull [] content, @NotNull VirtualFile highlightFile) {
    return readOnlyDocumentContent(project)
      .contextByHighlightFile(highlightFile)
      .buildFromBytes(content);
  }

  @NotNull
  @Override
  public DiffContent createBinary(@Nullable Project project,
                                  byte @NotNull [] content,
                                  @NotNull FileType type,
                                  @NotNull String fileName) throws IOException {
    return createBinaryImpl(project, content, type, DiffVcsFacade.getInstance().getFilePath(fileName), null);
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
                                         @NonNls @NotNull FilePath filePath,
                                         boolean readOnly) {
    Document document = createPsiDocument(project, content, fileType, filePath, readOnly);
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
                                            @NonNls @NotNull FilePath filePath,
                                            boolean readOnly) {
    if (project == null || project.isDefault()) return null;
    if (fileType == null || fileType.isBinary()) return null;

    LightVirtualFile file = new MyLightVirtualFile(filePath, fileType, content);
    file.setWritable(!readOnly);

    Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
    if (document == null) return null;

    ReadAction.run(() -> PsiDocumentManager.getInstance(project).getPsiFile(document));

    return document;
  }

  private static boolean isBinaryContent(byte @NotNull [] content, @NotNull FileType fileType) {
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


  @NotNull
  public static Charset guessCharset(@Nullable Project project, byte @NotNull [] content, @NotNull FilePath filePath) {
    return guessCharset(project, content, filePath.getFileType(), filePath, filePath.getVirtualFile(), null);
  }

  @NotNull
  private static Charset guessCharset(@Nullable Project project,
                                      byte @NotNull [] content,
                                      @Nullable FileType fileType,
                                      @NotNull FilePath filePath,
                                      @Nullable VirtualFile highlightFile,
                                      @Nullable Charset defaultCharset) {
    Charset bomCharset = CharsetToolkit.guessFromBOM(content);
    if (bomCharset != null) return bomCharset;

    if (defaultCharset != null) return defaultCharset;

    if (fileType != null && !fileType.isBinary()) {
      Charset fileTypeCharset = guessFileTypeCharset(project, content, fileType, filePath);
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

  @NotNull
  private static Charset takeCharsetOrGuessUTF(@NotNull Charset charset, byte @NotNull [] content) {
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

  @Nullable
  private static Charset guessCharsetFromContent(byte @NotNull [] content) {
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

  @Nullable
  private static Charset guessFileTypeCharset(@Nullable Project project,
                                              byte @NotNull [] content,
                                              @NotNull FileType fileType,
                                              @NotNull FilePath filePath) {
    LightVirtualFileBase file = new MyBinaryLightVirtualFile(filePath, fileType, content);
    if (project != null) {
      // pass Project to the EncodingManagerImpl to delegate to the EncodingProjectManagerImpl when needed
      return ProjectLocator.computeWithPreferredProject(file, project, () -> {
        return CharsetToolkit.forName(fileType.getCharset(file, content));
      });
    }
    else {
      return CharsetToolkit.forName(fileType.getCharset(file, content));
    }
  }

  @NotNull
  private DocumentContentBuilder readOnlyDocumentContent(@Nullable Project project) {
    return documentContent(project, true);
  }

  @Override
  @NotNull
  public DocumentContentBuilder documentContent(@Nullable Project project, boolean readOnly) {
    return new DocumentContentBuilderImpl(project).withReadOnly(readOnly);
  }

  private static class DocumentContentBuilderImpl implements DocumentContentBuilder {
    private final @Nullable Project project;
    private boolean readOnly;

    private @Nullable Context context;
    private @Nullable FilePath originalFilePath;
    private @Nullable @NonNls String fileName;
    private @Nullable @NonNls Charset defaultCharset;

    DocumentContentBuilderImpl(@Nullable Project project) {
      this.project = project;
    }

    @NotNull
    public DocumentContentBuilder withReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    @Override
    @NotNull
    public DocumentContentBuilder withFileName(@Nullable String fileName) {
      this.fileName = fileName;
      return this;
    }

    @Override
    public @NotNull DocumentContentBuilder withDefaultCharset(@Nullable Charset charset) {
      this.defaultCharset = charset;
      return this;
    }

    @Override
    @NotNull
    public DocumentContentBuilder contextByFileType(@Nullable FileType fileType) {
      if (fileType != null) {
        context = new Context.ByFileType(fileType);
      }
      return this;
    }

    @Override
    @NotNull
    public DocumentContentBuilder contextByFilePath(@Nullable FilePath filePath) {
      if (filePath != null) {
        context = new Context.ByFilePath(filePath);
        originalFilePath = filePath;
        fileName = filePath.getName();
      }
      return this;
    }

    @Override
    @NotNull
    public DocumentContentBuilder contextByHighlightFile(@Nullable VirtualFile file) {
      if (file != null) {
        context = new Context.ByHighlightFile(file);
        originalFilePath = DiffVcsFacade.getInstance().getFilePath(file);
        fileName = file.getName();
      }
      return this;
    }

    @Override
    @NotNull
    public DocumentContentBuilder contextByReferent(@Nullable DocumentContent referent) {
      if (referent != null) {
        context = new Context.ByReferent(referent);
        VirtualFile file = referent.getHighlightFile();
        if (file != null) {
          originalFilePath = DiffVcsFacade.getInstance().getFilePath(file);
          fileName = file.getName();
        }
      }
      return this;
    }

    @Override
    @NotNull
    public DocumentContent buildFromText(@NotNull String text, boolean respectLineSeparators) {
      FileType fileType = constructFileType();
      FilePath filePath = constructFilePath();

      TextContent textContent = TextContent.fromText(text, respectLineSeparators);

      Document document = createDocument(project, textContent.text, fileType, filePath, readOnly);
      return build(document, textContent);
    }

    @Override
    @NotNull
    public DocumentContent buildFromBytes(byte @NotNull [] content) {
      FileType fileType = constructFileType();
      FilePath filePath = constructFilePath();
      VirtualFile highlightFile = constructHighlightFile();
      Charset charset = guessCharset(project, content, fileType, filePath, highlightFile, defaultCharset);

      // decode bytes ourselves to detect incorrect charsets
      TextContent textContent = TextContent.fromBytes(content, charset);

      Document document = createDocument(project, textContent.text, fileType, filePath, readOnly);
      return build(document, textContent);
    }

    @NotNull
    private DocumentContent build(@NotNull Document document, @NotNull TextContent textContent) {
      DocumentContent documentContent = new ContextReferentDocumentContent(project, document, textContent, context);

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file != null && !file.isInLocalFileSystem()) {
        OutsidersPsiFileSupport.markFile(file, originalFilePath != null ? originalFilePath.getPath() : null);
      }

      if (fileName != null) {
        documentContent.putUserData(DiffUserDataKeysEx.FILE_NAME, fileName);
      }

      if (textContent.notification != null) {
        DiffUtil.addNotification(textContent.notification, documentContent);
      }

      return documentContent;
    }

    @Nullable
    private FileType constructFileType() {
      return context != null ? context.getContentType() : null;
    }

    @NotNull
    private FilePath constructFilePath() {
      if (originalFilePath != null) {
        return originalFilePath;
      }

      String name = fileName;
      if (name == null) {
        FileType fileType = constructFileType();
        name = "diff." + StringUtil.defaultIfEmpty(fileType != null ? fileType.getDefaultExtension() : null, "txt");
      }
      return DiffVcsFacade.getInstance().getFilePath(name);
    }

    @Nullable
    private VirtualFile constructHighlightFile() {
      return context != null ? context.getHighlightFile() : null;
    }
  }

  private static final class MyLightVirtualFile extends LightVirtualFile {
    private final FilePath myPath;

    private MyLightVirtualFile(@NotNull FilePath path, @Nullable FileType fileType, @NotNull String content) {
      super(path.getName(), fileType, content);
      myPath = path;
    }

    @NotNull
    @Override
    public String getPath() {
      return myPath.getPath();
    }

    @Override
    public String toString() {
      return "DiffContentFactory " + super.toString();
    }
  }

  private static class MyBinaryLightVirtualFile extends BinaryLightVirtualFile {
    private final FilePath myPath;

    MyBinaryLightVirtualFile(@NotNull FilePath path, @Nullable FileType type, byte @NotNull [] content) {
      super(path.getName(), type, content);
      myPath = path;
    }

    @NotNull
    @Override
    public String getPath() {
      return myPath.getPath();
    }

    @Override
    public String toString() {
      return "DiffContentFactory " + super.toString();
    }
  }

  private static final class FileReferentDocumentContent extends DocumentContentBase {
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

  private static final class ContentReferentDocumentContent extends DocumentContentBase {
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

  private static final class ContextReferentDocumentContent extends DocumentContentBase {
    @Nullable private final LineSeparator mySeparator;
    @Nullable private final Charset myCharset;
    @Nullable private final Boolean myBOM;

    @Nullable private final Context myReferent;

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
      return myReferent != null ? myReferent.getContentType() : null;
    }

    @Override
    public @Nullable VirtualFile getHighlightFile() {
      return myReferent != null ? myReferent.getHighlightFile() : null;
    }

    @Nullable
    @Override
    public LineSeparator getLineSeparator() {
      return mySeparator;
    }

    @Nullable
    @Override
    public Charset getCharset() {
      return myCharset;
    }

    @Override
    @Nullable
    public Boolean hasBom() {
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
        textContent.notification =
          DiffNotifications.createNotificationProvider(notificationText, LightColors.RED, EditorNotificationPanel.Status.Error);
      }
      return textContent;
    }
  }

  private interface Context {
    @Nullable VirtualFile getHighlightFile();

    @Nullable FileType getContentType();

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
      public @Nullable FileType getContentType() {
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
      public @Nullable FileType getContentType() {
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
      public @Nullable FileType getContentType() {
        return myFilePath.getFileType();
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
      public @Nullable FileType getContentType() {
        return myFileType;
      }
    }
  }
}
