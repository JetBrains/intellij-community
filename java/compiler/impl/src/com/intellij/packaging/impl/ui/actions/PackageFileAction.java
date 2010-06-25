/**
 * @author cdr
 */
package com.intellij.packaging.impl.ui.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactBySourceFileFinder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.DateFormat;
import java.util.*;

public class PackageFileAction extends AnAction {
  public PackageFileAction() {
    super(CompilerBundle.message("action.name.package.file"), CompilerBundle.message("action.description.package.file"), null);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean visible = false;
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      final List<VirtualFile> files = getFilesToPackage(e, project);
      if (!files.isEmpty()) {
        visible = true;
        e.getPresentation().setText(files.size() == 1 ? CompilerBundle.message("action.name.package.file") : CompilerBundle.message("action.name.package.files"));
      }
    }

    e.getPresentation().setVisible(visible);
  }

  @NotNull
  private static List<VirtualFile> getFilesToPackage(@NotNull AnActionEvent e, @NotNull Project project) {
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) return Collections.emptyList();

    List<VirtualFile> result = new ArrayList<VirtualFile>();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    for (VirtualFile file : files) {
      if (file == null || file.isDirectory() ||
          fileIndex.isInSourceContent(file) && compilerManager.isCompilableFileType(file.getFileType())) {
        return Collections.emptyList();
      }
      final Collection<? extends Artifact> artifacts = ArtifactBySourceFileFinder.getInstance(project).findArtifacts(file);
      for (Artifact artifact : artifacts) {
        if (!StringUtil.isEmpty(artifact.getOutputPath())) {
          result.add(file);
          break;
        }
      }
    }
    return result;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    FileDocumentManager.getInstance().saveAllDocuments();
    final List<VirtualFile> files = getFilesToPackage(event, project);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Packaging Files") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (final VirtualFile file : files) {
          indicator.checkCanceled();
          new ReadAction() {
            protected void run(final Result result) {
              try {
                PackageFileWorker.packageFile(file, project);
              }
              catch (IOException e) {
                Notifications.Bus.notify(
                  new Notification("Package File", "Cannot package file", CompilerBundle.message("message.tect.package.file.io.error", e.toString()),
                                   NotificationType.ERROR));
              }
            }
          }.execute();
        }
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            setStatusText(project, files);
          }
        });
      }
    });
  }

  private static void setStatusText(Project project, List<VirtualFile> files) {
    if (!files.isEmpty()) {
      StringBuilder fileNames = new StringBuilder();
      for (VirtualFile file : files) {
        if (fileNames.length() != 0) fileNames.append(", ");
        fileNames.append("'").append(file.getName()).append("'");
      }
      String time = DateFormat.getTimeInstance().format(new Date());
      final String statusText = CompilerBundle.message("status.text.file.has.been.packaged", files.size(), fileNames, time);
      WindowManager.getInstance().getStatusBar(project).setInfo(statusText);
    }
  }
}
