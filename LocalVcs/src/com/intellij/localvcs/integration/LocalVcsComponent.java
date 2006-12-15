package com.intellij.localvcs.integration;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class LocalVcsComponent implements ProjectComponent, SettingsSavingComponent {
  private Project myProject;
  private StartupManagerEx myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManager myFileManager;
  private FileTypeManager myTypeManager;
  private Storage myStorage;
  private LocalVcs myVcs;
  private LocalVcsService myService;

  public static LocalVcs getLocalVcsFor(Project p) {
    return p.getComponent(LocalVcsComponent.class).getLocalVcs();
  }

  public LocalVcsComponent(Project p, StartupManagerEx sm, ProjectRootManagerEx rm, VirtualFileManager fm, FileTypeManager tm) {
    myProject = p;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
    myTypeManager = tm;
  }

  public void initComponent() {
    if (isDisabled()) return;

    myStartupManager.registerPreStartupActivity(new Runnable() {
      public void run() {
        initVcs();
        initService();
      }
    });
  }

  protected void initVcs() {
    myStorage = new Storage(getStorageDir());
    myVcs = new LocalVcs(myStorage);
  }

  protected void initService() {
    FileFilter f = new FileFilter(myRootManager.getFileIndex(), myTypeManager);
    myService = new LocalVcsService(myVcs, myStartupManager, myRootManager, myFileManager, f);
  }

  public File getStorageDir() {
    File vcs = new File(getSystemPath(), "vcs");

    String prefix = myProject.getName();
    String postfix = Integer.toHexString(myProject.getProjectFilePath().hashCode());

    return new File(vcs, prefix + "." + postfix);
  }

  protected String getSystemPath() {
    return PathManager.getSystemPath();
  }

  public void save() {
    if (myVcs != null) myVcs.store();
  }

  public void disposeComponent() {
    if (isDisabled()) return;
    closeVcs();
    closeService();
  }

  protected void closeVcs() {
    myStorage.close();
  }

  protected void closeService() {
    myService.shutdown();
  }

  protected boolean isDisabled() {
    //if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    if (System.getProperty("localvcs.enabled") != null) return false;
    return true;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "NewLocalVcs";
  }

  public LocalVcs getLocalVcs() {
    if (isDisabled()) throw new RuntimeException("new local vcs is disabled");
    return myVcs;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
