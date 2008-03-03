package com.intellij.ide;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.platform.ProjectBaseDirectory;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
@State(
  name = "RecentDirectoryProjectsManager",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RecentDirectoryProjectsManager extends RecentProjectsManagerBase {
  public RecentDirectoryProjectsManager(ProjectManager projectManager, MessageBus messageBus) {
    super(projectManager, messageBus);
  }

  @Nullable
  protected String getProjectPath(final Project project) {
    final ProjectBaseDirectory baseDir = ProjectBaseDirectory.getInstance(project);
    if (baseDir.BASE_DIR != null) {
      return FileUtil.toSystemDependentName(baseDir.BASE_DIR.getPath());
    }
    return null;
  }

  protected void doOpenProject(final String projectPath, final Project projectToClose) {
    final VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(projectPath));
    if (projectDir != null) {
      PlatformProjectOpenProcessor.getInstance().doOpenProject(projectDir, projectToClose, false);
    }
  }
}
