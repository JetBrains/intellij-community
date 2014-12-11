package com.intellij.openapi.util.diff.actions.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.contents.*;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowOldDiffAction extends DumbAwareAction {
  @NonNls public static final Object DO_NOT_TRY_MIGRATE = "doNotTryMigrate";

  public ShowOldDiffAction() {
    super("Show in Old diff", null, AllIcons.Diff.Diff);
  }

  @Override
  public void update(AnActionEvent e) {
    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
    if (request == null || !(request instanceof ContentDiffRequest)) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    if (contents.length != 2) {
      e.getPresentation().setEnabled(false);
      return;
    }

    for (DiffContent content : contents) {
      if (content instanceof EmptyContent ||
          content instanceof DocumentContent ||
          content instanceof FileContent ||
          content instanceof DirectoryContent) {
        continue;
      }

      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ContentDiffRequest request = (ContentDiffRequest)e.getRequiredData(DiffDataKeys.DIFF_REQUEST);
    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    String[] titles = request.getContentTitles();

    SimpleDiffRequest newRequest = new SimpleDiffRequest(e.getProject(), request.getWindowTitle());
    newRequest.setContentTitles(titles[0], titles[1]);
    newRequest.setContents(convert(e.getProject(), contents[0]), convert(e.getProject(), contents[1]));
    newRequest.addHint(DO_NOT_TRY_MIGRATE);

    DiffManager.getInstance().getDiffTool().show(newRequest);
  }

  @NotNull
  private static com.intellij.openapi.diff.DiffContent convert(@Nullable Project project, @NotNull DiffContent content) {
    if (content instanceof EmptyContent) return SimpleContent.createEmpty();

    if (content instanceof DocumentContent) {
      Document document = ((DocumentContent)content).getDocument();
      return new com.intellij.openapi.diff.DocumentContent(project, document, content.getContentType());
    }
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();
      return new com.intellij.openapi.diff.FileContent(project, file);
    }
    if (content instanceof DirectoryContent) {
      VirtualFile file = ((DirectoryContent)content).getFile();
      return new com.intellij.openapi.diff.FileContent(project, file);
    }

    throw new IllegalArgumentException();
  }
}
