package com.intellij.ide.ui;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.List;

@ApiStatus.Experimental
public class IdeUiService {

  public void revealFile(File file) {
  }

  public NonProjectFileWritingAccessProvider.UnlockOption askForUnlock(@NotNull Project project,
                                                                       List<VirtualFile> files) {
    return null;
  }

  public boolean isFileRecentlyChanged(Project project, VirtualFile file) {

    return false;
  }

  public void logUsageEvent(Class clazz, String groupId, String eventId) {

  }

  public void systemNotify(String title, String text) {

  }

  public DataContext createUiDataContext(Component component) {
    return null;
  }

  public Component getComponentFromRecentMouseEvent() {
    return null;
  }

  public static IdeUiService getInstance() {
    return ApplicationManager.getApplication().getService(IdeUiService.class);
  }
}
