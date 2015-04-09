package com.intellij.diff.contents;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class FileAwareDocumentContent extends DocumentContentImpl {
  @Nullable private final Project myProject;
  @Nullable private final VirtualFile myLocalFile;

  public FileAwareDocumentContent(@Nullable Project project,
                                  @NotNull Document document,
                                  @Nullable FileType fileType,
                                  @Nullable VirtualFile localFile,
                                  @Nullable LineSeparator separator,
                                  @Nullable Charset charset) {
    super(document, fileType, localFile, separator, charset);
    myProject = project;
    myLocalFile = localFile;
  }

  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    if (myProject == null || myLocalFile == null) return null;
    return new OpenFileDescriptor(myProject, myLocalFile, offset);
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull String content, @NotNull FilePath path) {
    return new Builder(project).init(path).create(content).build();
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull String content, @NotNull VirtualFile file) {
    return new Builder(project).init(file).create(content).build();
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull byte[] content, @NotNull FilePath path) {
    return new Builder(project).init(path).create(content).build();
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull byte[] content, @NotNull VirtualFile file) {
    return new Builder(project).init(file).create(content).build();
  }

  private static class Builder {
    private final Project myProject;
    private Document myDocument;
    private FileType myFileType;
    private VirtualFile myLocalFile;
    private LineSeparator mySeparator;
    private Charset myCharset;

    public Builder(@Nullable Project project) {
      myProject = project;
    }

    //
    // Impl
    //

    @NotNull
    private Builder init(@NotNull FilePath path) {
      myLocalFile = LocalFileSystem.getInstance().findFileByPath(path.getPath());
      myFileType = myLocalFile != null ? myLocalFile.getFileType() : path.getFileType();
      myCharset = myLocalFile != null ? myLocalFile.getCharset() : path.getCharset(myProject);
      return this;
    }

    @NotNull
    private Builder init(@NotNull VirtualFile file) {
      myLocalFile = file;
      myFileType = file.getFileType();
      myCharset = file.getCharset();
      return this;
    }

    @NotNull
    private Builder create(@NotNull String content) {
      mySeparator = StringUtil.detectSeparators(content);
      myDocument = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(content));
      myDocument.setReadOnly(true);
      return this;
    }

    @NotNull
    private Builder create(@NotNull byte[] content) {
      assert myCharset != null;
      // TODO: detect charset like in LoadTextUtil (Native2Ascii, etc) ?
      Pair<String, Charset> pair = CharsetToolkit.bytesToStringWithCharset(content, myCharset);
      myCharset = pair.second;
      return create(pair.first);
    }

    @NotNull
    public FileAwareDocumentContent build() {
      if (FileTypes.UNKNOWN.equals(myFileType)) myFileType = PlainTextFileType.INSTANCE;
      return new FileAwareDocumentContent(myProject, myDocument, myFileType, myLocalFile, mySeparator, myCharset);
    }
  }
}
