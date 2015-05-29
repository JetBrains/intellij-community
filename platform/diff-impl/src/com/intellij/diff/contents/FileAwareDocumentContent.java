package com.intellij.diff.contents;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class FileAwareDocumentContent extends DocumentContentImpl {
  @Nullable private final Project myProject;

  public FileAwareDocumentContent(@Nullable Project project,
                                  @NotNull Document document,
                                  @Nullable FileType fileType,
                                  @Nullable VirtualFile highlightFile,
                                  @Nullable LineSeparator separator,
                                  @Nullable Charset charset) {
    super(document, fileType, highlightFile, separator, charset);
    myProject = project;
  }

  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    if (myProject == null || getHighlightFile() == null) return null;
    return new OpenFileDescriptor(myProject, getHighlightFile(), offset);
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull String content, @NotNull FilePath path) {
    return new Builder(project).init(path).create(content).build();
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull String content, @NotNull VirtualFile highlightFile) {
    return new Builder(project).init(highlightFile).create(content).build();
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull byte[] content, @NotNull FilePath path) {
    return new Builder(project).init(path).create(content).build();
  }

  @NotNull
  public static FileAwareDocumentContent create(@Nullable Project project, @NotNull byte[] content, @NotNull VirtualFile highlightFile) {
    return new Builder(project).init(highlightFile).create(content).build();
  }

  private static class Builder {
    private final Project myProject;
    private Document myDocument;
    private FileType myFileType;
    private VirtualFile myHighlightFile;
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
      myHighlightFile = path.getVirtualFile();
      myFileType = path.getFileType();
      myCharset = path.getCharset(myProject);
      return this;
    }

    @NotNull
    private Builder init(@NotNull VirtualFile highlightFile) {
      myHighlightFile = highlightFile;
      myFileType = highlightFile.getFileType();
      myCharset = highlightFile.getCharset();
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
      return create(CharsetToolkit.decodeString(content, myCharset));
    }

    @NotNull
    public FileAwareDocumentContent build() {
      if (FileTypes.UNKNOWN.equals(myFileType)) myFileType = PlainTextFileType.INSTANCE;
      return new FileAwareDocumentContent(myProject, myDocument, myFileType, myHighlightFile, mySeparator, myCharset);
    }
  }
}
