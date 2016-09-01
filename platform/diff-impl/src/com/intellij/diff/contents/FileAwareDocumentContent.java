package com.intellij.diff.contents;

import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.LightColors;
import com.intellij.util.LineSeparator;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.CharacterCodingException;
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

  @Override
  public Navigatable getNavigatable(@NotNull LineCol position) {
    if (myProject == null || getHighlightFile() == null || !getHighlightFile().isValid()) return null;
    return new MyNavigatable(myProject, getHighlightFile(), getDocument(), position);
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
    private String myFileName;

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
      myFileName = path.getName();
      return this;
    }

    @NotNull
    private Builder init(@NotNull VirtualFile highlightFile) {
      myHighlightFile = highlightFile;
      myFileType = highlightFile.getFileType();
      mySuggestedCharset = highlightFile.getCharset();
      myFileName = highlightFile.getName();
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
    private JComponent createNotification() {
      if (!myMalformedContent) return null;
      assert mySuggestedCharset != null;

      String text = "Content was decoded with errors (using " + "'" + mySuggestedCharset.name() + "' charset)";
      return DiffNotifications.createNotification(text, LightColors.RED);
    }

    @NotNull
    public FileAwareDocumentContent build() {
      if (FileTypes.UNKNOWN.equals(myFileType)) myFileType = PlainTextFileType.INSTANCE;
      FileAwareDocumentContent content
        = new FileAwareDocumentContent(myProject, myDocument, myFileType, myHighlightFile, mySeparator, myCharset);
      DiffUtil.addNotification(createNotification(), content);
      content.putUserData(DiffUserDataKeysEx.FILE_NAME, myFileName);
      return content;
    }
  }

  private static class MyNavigatable implements Navigatable {
    @NotNull private final Project myProject;
    @NotNull private final VirtualFile myTargetFile;
    @NotNull private final Document myDocument;
    @NotNull private final LineCol myPosition;

    public MyNavigatable(@NotNull Project project, @NotNull VirtualFile targetFile, @NotNull Document document, @NotNull LineCol position) {
      myProject = project;
      myTargetFile = targetFile;
      myDocument = document;
      myPosition = position;
    }

    @Override
    public void navigate(boolean requestFocus) {
      Document targetDocument = FileDocumentManager.getInstance().getDocument(myTargetFile);
      LineCol targetPosition = translatePosition(myDocument, targetDocument, myPosition);
      OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, myTargetFile, targetPosition.line, targetPosition.column);
      if (descriptor.canNavigate()) descriptor.navigate(true);
    }

    @Override
    public boolean canNavigate() {
      return myTargetFile.isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @NotNull
    private static LineCol translatePosition(@NotNull Document fromDocument, @Nullable Document toDocument, @NotNull LineCol position) {
      try {
        if (toDocument == null) return position;
        int targetLine = Diff.translateLine(fromDocument.getCharsSequence(), toDocument.getCharsSequence(), position.line, true);
        return new LineCol(targetLine, position.column);
      }
      catch (FilesTooBigForDiffException ignore) {
        return position;
      }
    }
  }
}
