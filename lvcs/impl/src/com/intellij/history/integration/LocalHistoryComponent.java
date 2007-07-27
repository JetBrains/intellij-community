package com.intellij.history.integration;

import com.intellij.history.*;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.ThreadSafeLocalVcs;
import com.intellij.history.core.storage.Storage;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;

public class LocalHistoryComponent extends LocalHistory implements ProjectComponent {
  private Project myProject;
  private StartupManagerEx myStartupManager;
  private ProjectRootManagerEx myRootManager;
  private VirtualFileManagerEx myFileManager;
  private CommandProcessor myCommandProcessor;
  private Storage myStorage;
  private ILocalVcs myVcs;
  private LocalVcs myVcsImpl;
  private LocalHistoryService myService;
  private LocalHistoryConfiguration myConfiguration;

  @TestOnly
  public static LocalHistoryComponent getComponentInstance(Project p) {
    return (LocalHistoryComponent)p.getComponent(LocalHistory.class);
  }

  // todo bad method - extend interface instead
  public static ILocalVcs getLocalVcsFor(Project p) {
    return getComponentInstance(p).myVcs;
  }

  @TestOnly
  public static LocalVcs getLocalVcsImplFor(Project p) {
    return getComponentInstance(p).myVcsImpl;
  }

  public LocalHistoryComponent(Project p,
                               StartupManager sm,
                               ProjectRootManagerEx rm,
                               VirtualFileManagerEx fm,
                               CommandProcessor cp,
                               LocalHistoryConfiguration c) {
    myProject = p;
    myStartupManager = (StartupManagerEx)sm;
    myRootManager = rm;
    myFileManager = fm;
    myCommandProcessor = cp;
    myConfiguration = c;
  }

  public void initComponent() {
    if (isDefaultProject()) return;

    myStartupManager.registerPreStartupActivity(new Runnable() {
      public void run() {
        initVcs();
        initService();
      }
    });
  }

  protected void initVcs() {
    myStorage = new Storage(getStorageDir());
    myVcsImpl = new LocalVcs(myStorage) {
      @Override
      protected long getPurgingPeriod() {
        return myConfiguration.PURGE_PERIOD;
      }
    };
    myVcs = new ThreadSafeLocalVcs(myVcsImpl);
  }

  protected void initService() {
    myService = new LocalHistoryService(myVcs, createIdeaGateway(), myConfiguration, myStartupManager, myRootManager, myFileManager,
                                        myCommandProcessor);
  }

  public File getStorageDir() {
    File vcsDir = new File(getSystemPath(), "LocalHistory");
    return new File(vcsDir, myProject.getLocationHash());
  }

  protected String getSystemPath() {
    return PathManager.getSystemPath();
  }

  public void save() {
    if (isDefaultProject()) return;

    if (myVcs != null) myVcs.save();
  }

  public void disposeComponent() {
    if (isDefaultProject()) return;

    // save could not be called if user had canceled save of project files
    // so we have to force save. But that will be ignored if where were
    // no changes since last save, so there is no performance issues here
    save();

    closeVcs();
    closeService();

    cleanupStorageAfterTestCase();
  }

  protected void cleanupStorageAfterTestCase() {
    if (isUnitTestMode()) FileUtil.delete(getStorageDir());
  }

  protected boolean isUnitTestMode() {
    return ApplicationManagerEx.getApplicationEx().isUnitTestMode();
  }

  public void closeVcs() {
    if (myStorage != null) {
      myStorage.close();
    }
  }

  protected void closeService() {
    if (myService != null) {
      myService.shutdown();
    }
  }

  protected boolean isDefaultProject() {
    return myProject.isDefault();
  }

  @Override
  protected LocalHistoryAction startAction(String name) {
    // todo temporary means to catch bug IDEADEV-19421 
    assert myProject != null;
    assert !isDefaultProject();
    assert myVcs != null;
    assert myService != null;

    return myService.startAction(name);
  }

  @Override
  protected void putSystemLabel(String name, int color) {
    myVcs.putSystemLabel(name, color);
  }

  @Override
  protected Checkpoint putCheckpoint() {
    return new CheckpointImpl(createIdeaGateway(), myVcs);
  }

  @Override
  protected byte[] getByteContent(VirtualFile f, RevisionTimestampComparator c) {
    if (!isUnderControl(f)) return null;
    return myVcs.getByteContent(f.getPath(), c);
  }

  @Override
  protected boolean isUnderControl(VirtualFile f) {
    return createIdeaGateway().getFileFilter().isAllowedAndUnderContentRoot(f);
  }

  @Override
  protected boolean hasUnavailableContent(VirtualFile f) {
    return myVcs.getEntry(f.getPath()).hasUnavailableContent();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Project Local History";
  }

  private IdeaGateway createIdeaGateway() {
    return new IdeaGateway(myProject);
  }

  public ILocalVcs getLocalVcs() {
    return myVcs;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
