package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Belyaev
 */
public class ProjectUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.ProjectUtil");
  @NonNls public static final String DIRECTORY_BASED_PROJECT_DIR = ".idea";

  private ProjectUtil() {
  }

  public static void updateLastProjectLocation(final String projectFilePath) {
    File lastProjectLocation = new File(projectFilePath).getParentFile();
    if (lastProjectLocation == null) { // the immediate parent of the ipr file
      return;
    }
    lastProjectLocation = lastProjectLocation.getParentFile(); // the candidate directory to be saved
    if (lastProjectLocation == null) {
      return;
    }
    String path = lastProjectLocation.getPath();
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      LOG.info(e);
      return;
    }
    GeneralSettings.getInstance().setLastProjectLocation(path.replace(File.separatorChar, '/'));
  }

  /**
   * @param project cannot be null
   */
  public static boolean closeProject(@NotNull Project project) {
    if (!ProjectManagerEx.getInstanceEx().closeProject(project)) return false;
    Disposer.dispose(project);
    return true;
  }

  /**
   * @param path                project file path
   * @param projectToClose      currently active project
   * @param forceOpenInNewFrame forces opening in new frame
   * @return project by path if the path was recognized as IDEA project file or one of the project formats supported by
   *         installed importers (regardless of opening/import result)
   *         null otherwise
   */
  @Nullable
  public static Project openOrImport(@NotNull final String path, final Project projectToClose, boolean forceOpenInNewFrame) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);

    if (virtualFile == null) return null;

    if (path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) || virtualFile.isDirectory() && virtualFile.findChild(
        DIRECTORY_BASED_PROJECT_DIR) != null) {
      return openProject(path, projectToClose, forceOpenInNewFrame);
    }
    else {
      ProjectOpenProcessor provider = getImportProvider(virtualFile);
      if (provider != null) {
        return provider.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);
      }
    }
    return null;
  }

  @Nullable
  public static void saveInNewFormat(@NotNull final String path, final Runnable postRunnable) throws Exception {

    final File iprFile = new File(path);

    File ideaDir = new File(iprFile.getParentFile(), DIRECTORY_BASED_PROJECT_DIR);
    File iwsFile = new File(iprFile.getParentFile(), getProjectName(iprFile) + ".iws");

    ideaDir.mkdirs();

    File convertXml = new File(ideaDir, "convert.xml");
    FileUtil.copy(iprFile, convertXml);
    if (iwsFile.isFile()) {
      FileUtil.copy(iwsFile, new File(ideaDir, "workspace.xml"));
    }

    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByPath(path);

    localFileSystem.refreshAndFindFileByIoFile(ideaDir);

    if (virtualFile == null) return;

    if (path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();

      System.setProperty("convert.project.mode", "on");

      final Exception[] ex = new Exception[1];

      try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
          public void run() {
            try {
              final Project project = projectManager.loadProject(iprFile.getParentFile().getPath()) ;

              ExtensionPoint[] points = Extensions.getRootArea().getExtensionPoints();
              for (ExtensionPoint point : points) {
                point.getExtensions();
              }

              ApplicationManager.getApplication().invokeLater(new Runnable(){
                public void run() {
                  project.save();
                  projectManager.closeProject(project);

                  if (postRunnable != null) postRunnable.run();
                }
              });

            }
            catch (Exception e1) {
              ex[0] = e1;
            }

          }
        }, "Converting Project", false, null);

        if (ex[0] != null) throw ex[0];


      }
      finally {
        System.setProperty("convert.project.mode", "off");

        FileUtil.delete(convertXml);

      }

    }
  }

  private static String getProjectName(final File iprFile) {
    return FileUtil.getNameWithoutExtension(iprFile.getName());
  }

  @Nullable
  public static ProjectOpenProcessor getImportProvider(VirtualFile file) {
    for (ProjectOpenProcessor provider : Extensions.getExtensions(ProjectOpenProcessor.EXTENSION_POINT_NAME)) {
      if (provider.canOpenProject(file)) {
        return provider;
      }
    }
    return null;
  }

  @Nullable
  public static Project openProject(final String path, Project projectToClose, boolean forceOpenInNewFrame) {
    File file = new File(path);
    if (!file.exists()) {
      Messages.showMessageDialog(IdeBundle.message("error.project.file.does.not.exist", path), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      if (isSameProject(path, project)) {
        focusProjectWindow(project);
        return project;
      }
    }

    if (!forceOpenInNewFrame && openProjects.length > 0) {
      int exitCode = Messages.showDialog(IdeBundle.message("prompt.open.project.in.new.frame"), IdeBundle.message("title.open.project"),
                                         new String[]{IdeBundle.message("button.newframe"), IdeBundle.message("button.existingframe"),
                                           CommonBundle.getCancelButtonText()}, 1, Messages.getQuestionIcon());
      if (exitCode == 1) { // "No" option
        if (!closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1])) return null;
      }
      else if (exitCode != 0) { // not "Yes"
        return null;
      }
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    try {
      project = projectManager.loadAndOpenProject(path);
    }
    catch (IOException e) {
      Messages.showMessageDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                                 IdeBundle.message("title.cannot.load.project"), Messages.getErrorIcon());
    }
    catch (JDOMException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    catch (InvalidDataException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    return project;
  }

  private static boolean isSameProject(String path, Project p) {
    String projectPath = ((ProjectImpl)p).getStateStore().getProjectFilePath();
    String p1 = FileUtil.toSystemIndependentName(path);
    String p2 = FileUtil.toSystemIndependentName(projectPath);
    return FileUtil.pathsEqual(p1, p2);
  }

  private static void focusProjectWindow(Project p) {
    JFrame f = WindowManager.getInstance().getFrame(p);
    f.requestFocus();
  }

  public static String getInitialModuleRootPath(String projectFilePath) {
    return new File(projectFilePath).getParentFile().getAbsolutePath();
  }


  public static boolean isProjectOrWorkspaceFile(final VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getFileType());
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file,
                                                 final FileType fileType) {
    final VirtualFile parent = file.getParent();
    return fileType instanceof WorkspaceFileType ||
           fileType instanceof ProjectFileType ||
           fileType instanceof ModuleFileType ||
           (parent != null && parent.getName().equals(DIRECTORY_BASED_PROJECT_DIR));
  }
}
