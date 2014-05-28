/**
 * @author cdr
 */
package com.intellij.packaging.impl.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.artifacts.ArtifactBySourceFileFinder;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PackageFileAction extends AnAction {
  private static final SyncDateFormat TIME_FORMAT = new SyncDateFormat(new SimpleDateFormat("h:mm:ss a"));

  public PackageFileAction() {
    super(CompilerBundle.message("action.name.package.file"), CompilerBundle.message("action.description.package.file"), null);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean visible = false;
    final Project project = e.getData(CommonDataKeys.PROJECT);
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
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null) return Collections.emptyList();

    List<VirtualFile> result = new ArrayList<VirtualFile>();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    for (VirtualFile file : files) {
      if (file == null || file.isDirectory() ||
          fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) && compilerManager.isCompilableFileType(file.getFileType())) {
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
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    FileDocumentManager.getInstance().saveAllDocuments();
    final List<VirtualFile> files = getFilesToPackage(event, project);
    Artifact[] allArtifacts = ArtifactManager.getInstance(project).getArtifacts();
    PackageFileWorker.startPackagingFiles(project, files, allArtifacts, new Runnable() {
      public void run() {
        setStatusText(project, files);
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
      String time = TIME_FORMAT.format(Clock.getTime());
      final String statusText = CompilerBundle.message("status.text.file.has.been.packaged", files.size(), fileNames, time);
      final StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        statusBar.setInfo(statusText);
      }
    }
  }
}
