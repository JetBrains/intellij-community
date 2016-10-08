package com.intellij.diff.contents;

import com.intellij.diff.util.LineCol;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
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
                                  @Nullable Charset charset,
                                  @Nullable Boolean bom) {
    super(document, fileType, highlightFile, separator, charset, bom);
    myProject = project;
  }

  @Override
  public Navigatable getNavigatable(@NotNull LineCol position) {
    if (myProject == null || getHighlightFile() == null || !getHighlightFile().isValid()) return null;
    return new MyNavigatable(myProject, getHighlightFile(), getDocument(), position);
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
