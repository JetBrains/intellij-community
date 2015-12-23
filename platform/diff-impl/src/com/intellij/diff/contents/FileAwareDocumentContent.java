package com.intellij.diff.contents;

import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffUserDataKeys;
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
import com.intellij.ui.LightColors;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

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

  @Override
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    if (myProject == null || getHighlightFile() == null || !getHighlightFile().isValid()) return null;
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
    private Charset mySuggestedCharset;
    private boolean myMalformedContent;

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
      mySuggestedCharset = path.getCharset(myProject);
      return this;
    }

    @NotNull
    private Builder init(@NotNull VirtualFile highlightFile) {
      myHighlightFile = highlightFile;
      myFileType = highlightFile.getFileType();
      mySuggestedCharset = highlightFile.getCharset();
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
      assert mySuggestedCharset != null;

      myCharset = mySuggestedCharset;
      try {
        String text = CharsetToolkit.tryDecodeString(content, mySuggestedCharset);
        return create(text);
      }
      catch (CharacterCodingException e) {
        String text = CharsetToolkit.decodeString(content, mySuggestedCharset);
        myMalformedContent = true;
        return create(text);
      }
    }

    @Nullable
    private List<JComponent> createNotifications() {
      if (!myMalformedContent) return null;
      assert mySuggestedCharset != null;

      String text = "Content was decoded with errors (using " + "'" + mySuggestedCharset.name() + "' charset)";
      JComponent notification = DiffNotifications.createNotification(text, LightColors.RED);
      return Collections.singletonList(notification);
    }

    @NotNull
    public FileAwareDocumentContent build() {
      if (FileTypes.UNKNOWN.equals(myFileType)) myFileType = PlainTextFileType.INSTANCE;
      FileAwareDocumentContent content
        = new FileAwareDocumentContent(myProject, myDocument, myFileType, myHighlightFile, mySeparator, myCharset);
      content.putUserData(DiffUserDataKeys.NOTIFICATIONS, createNotifications());
      return content;
    }
  }
}
