package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.ex.DiffContentFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class CompareFiles extends BaseDiffAction {
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile[] virtualFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles == null || virtualFiles.length != 2) {
      presentation.setVisible(false);
      return;
    }
    DiffRequest diffRequest = getDiffRequest(project, virtualFiles);
    if (diffRequest == null) {
      presentation.setVisible(false);
      return;
    }
    final boolean canShow = DiffManager.getInstance().getDiffTool().canShow(diffRequest);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(canShow);      
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(canShow);
    }
  }

  protected DiffRequest getDiffData(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final VirtualFile[] data = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (data == null || data.length != 2) {
      return null;
    }
    return getDiffRequest(project, data);
  }

  @Nullable
  private static DiffRequest getDiffRequest(Project project, VirtualFile[] files) {
    if (files == null || files.length != 2) return null;
    String title = DiffBundle.message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
                                      getVirtualFileContentTitle(files [0]),
                                      getVirtualFileContentTitle(files [1]));
    SimpleDiffRequest diffRequest = DiffContentFactory.compareVirtualFiles(project, files[0], files[1], title);
    if (diffRequest == null) return null;
    diffRequest.setContentTitles(getVirtualFileContentTitle(files [0]),
                                 getVirtualFileContentTitle(files [1]));
    return diffRequest;
  }
}
