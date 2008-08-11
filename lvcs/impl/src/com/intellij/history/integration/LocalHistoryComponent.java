package com.intellij.history.integration;

import com.intellij.history.*;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.ThreadSafeLocalVcs;
import com.intellij.history.core.tree.Entry;
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
  private LocalHistoryConfiguration myConfiguration;
  private Storage myStorage;
  private ILocalVcs myVcs;
  private LocalVcs myVcsImpl;
  private LocalHistoryService myService;
  private IdeaGateway myGateway;

  private boolean isInitialized;

  @TestOnly
  public static LocalHistoryComponent getComponentInstance(Project p) {
    return (LocalHistoryComponent)p.getComponent(LocalHistory.class);
  }

  // todo bad method - extend interface instead
  public static ILocalVcs getLocalVcsFor(Project p) {
    return getComponentInstance(p).getLocalVcs();
  }

  @TestOnly
  public static LocalVcs getLocalVcsImplFor(Project p) {
    return getComponentInstance(p).getLocalVcsImpl();
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
        init();
      }
    });
  }

  protected void init() {
    initVcs();
    initService();
    isInitialized = true;
  }

  protected void initVcs() {
    myStorage = new Storage(getStorageDir());

    myVcsImpl = new LocalVcs(myStorage);
    myVcs = new ThreadSafeLocalVcs(myVcsImpl);
  }

  protected void initService() {
    myGateway = new IdeaGateway(myProject);
    myService = new LocalHistoryService(myVcs,
                                        myGateway,
                                        myConfiguration,
                                        myStartupManager,
                                        myRootManager,
                                        myFileManager,
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
    if (!isInitialized) return;
    myVcs.save();
  }

  public void disposeComponent() {
    if (!isInitialized) return;

    myVcs.purgeObsoleteAndSave(myConfiguration.PURGE_PERIOD);

    closeVcs();
    closeService();

    cleanupStorageAfterTestCase();

    isInitialized = false;
  }

  protected void cleanupStorageAfterTestCase() {
    if (isUnitTestMode()) FileUtil.delete(getStorageDir());
  }

  protected boolean isUnitTestMode() {
    return ApplicationManagerEx.getApplicationEx().isUnitTestMode();
  }

  public void closeVcs() {
    if (!isInitialized) return;
    myStorage.close();
  }

  protected void closeService() {
    if (!isInitialized) return;
    myService.shutdown();
  }

  protected boolean isDefaultProject() {
    return myProject.isDefault();
  }

  @Override
  protected LocalHistoryAction startAction(String name) {
    if (!isInitialized) return LocalHistoryAction.NULL;
    return myService.startAction(name);
  }

  @Override
  protected void putUserLabel(String name) {
    if (!isInitialized) return;
    myGateway.registerUnsavedDocuments(myVcs);
    myVcs.putUserLabel(name);
  }

  @Override
  protected void putUserLabel(VirtualFile f, String name) {
    if (!isInitialized) return;
    myGateway.registerUnsavedDocuments(myVcs);
    myVcs.putUserLabel(f.getPath(), name);
  }

  @Override
  protected void putSystemLabel(String name, int color) {
    if (!isInitialized) return;
    myGateway.registerUnsavedDocuments(myVcs);
    myVcs.putSystemLabel(name, color);
  }

  @Override
  protected Checkpoint putCheckpoint() {
    if (!isInitialized) return new Checkpoint.NullCheckpoint();

    return new CheckpointImpl(myGateway, myVcs);
  }

  @Override
  protected byte[] getByteContent(VirtualFile f, FileRevisionTimestampComparator c) {
    if (!isInitialized) return null;
    if (!isUnderControl(f)) return null;
    return myVcs.getByteContent(f.getPath(), c);
  }

  @Override
  protected boolean isUnderControl(VirtualFile f) {
    if (!isInitialized) return false;
    return myGateway.getFileFilter().isAllowedAndUnderContentRoot(f);
  }

  @Override
  protected boolean hasUnavailableContent(VirtualFile f) {
    if (!isInitialized) return false;
    if (!isUnderControl(f)) return false;

    // TODO IDEADEV-21269 bug hook
    if (!f.isValid()) {
      LocalHistoryLog.LOG.warn("File is invalid: " + f);
      return false;
    }

    Entry entry = myVcs.findEntry(f.getPath());
    // TODO hook for IDEADEV-26645
    if (entry == null) {
      LocalHistoryLog.LOG.warn("Entry does not exist for " + f);
      return false;
    }
    return entry.hasUnavailableContent();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Local History";
  }

  public ILocalVcs getLocalVcs() {
    return myVcs;
  }

  @TestOnly
  public LocalVcs getLocalVcsImpl() {
    return myVcsImpl;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
