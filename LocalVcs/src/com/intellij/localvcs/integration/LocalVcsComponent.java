package com.intellij.localvcs.integration;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Storage;
import com.intellij.localvcs.ThreadSafeLocalVcs;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

// todo get rid of all singletons
public class LocalVcsComponent implements ProjectComponent, ILocalVcsComponent {
  private Project myProject;
  private StartupManagerEx myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManagerEx myFileManager;
  private FileDocumentManager myDocumentManager;
  private FileTypeManager myTypeManager;
  private Storage myStorage;
  private ILocalVcs myVcs;
  private LocalVcsService myService;

  // todo bad method - extend interface insteat
  public static ILocalVcs getLocalVcsFor(Project p) {
    return ((LocalVcsComponent)getInstance(p)).getLocalVcs();
  }

  // todo try to get rid of this method (and use startActionFor(Project) instead
  public static ILocalVcsComponent getInstance(Project p) {
    return p.getComponent(ILocalVcsComponent.class);
  }

  public LocalVcsComponent(Project p, StartupManagerEx sm, ProjectRootManagerEx rm, VirtualFileManagerEx fm, FileDocumentManager dm,
                           FileTypeManager tm) {
    myProject = p;
    myStartupManager = sm;
    myRootManager = rm;
    myFileManager = fm;
    myDocumentManager = dm;
    myTypeManager = tm;
  }

  public void initComponent() {
    if (!isEnabled()) return;

    // todo review startup order
    myStartupManager.registerPreStartupActivity(new Runnable() {
      public void run() {
        initVcs();
        initService();
      }
    });
  }

  protected void initVcs() {
    myStorage = new Storage(getStorageDir());
    myVcs = new ThreadSafeLocalVcs(new LocalVcs(myStorage));
  }

  protected void initService() {
    FileFilter f = new FileFilter(myRootManager.getFileIndex(), myTypeManager);
    myService = new LocalVcsService(myVcs, myStartupManager, myRootManager, myFileManager, myDocumentManager, f);
  }

  public File getStorageDir() {
    File vcs = new File(getSystemPath(), "vcs");

    String prefix = myProject.getName();
    String postfix = myProject.getLocationHash();

    return new File(vcs, prefix + "." + postfix);
  }

  protected String getSystemPath() {
    return PathManager.getSystemPath();
  }

  public void save() {
    if (!isEnabled()) return;
    if (myVcs != null) myVcs.save();
  }

  public void disposeComponent() {
    if (!isEnabled()) return;
    closeVcs();
    closeService();
  }

  protected void closeVcs() {
    myStorage.close();
  }

  protected void closeService() {
    myService.shutdown();
  }

  public boolean isEnabled() {
    // todo dont forget to chenge CommonLvcs too
    if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return true;
    if (ApplicationManagerEx.getApplicationEx().isInternal()) return true;
    return System.getProperty("localvcs.disabled") == null;
  }

  public LocalVcsAction startAction(String label) {
    if (!isEnabled()) return LocalVcsAction.NULL;
    return myService.startAction(label);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "NewLocalVcs";
  }

  public ILocalVcs getLocalVcs() {
    if (!isEnabled()) throw new RuntimeException("new local vcs is disabled");
    return myVcs;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
