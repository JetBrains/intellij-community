/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.integration;

import com.intellij.history.*;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalHistoryComponent extends LocalHistory implements ProjectComponent {
  private final Project myProject;
  private final StartupManagerEx myStartupManager;
  private final ProjectRootManagerEx myRootManager;
  private final VirtualFileManagerEx myFileManager;
  private final CommandProcessor myCommandProcessor;
  private final LocalHistoryConfiguration myConfiguration;
  private Storage myStorage;
  private LocalVcs myVcs;
  private LocalHistoryService myService;
  private IdeaGateway myGateway;

  private final AtomicBoolean isInitialized = new AtomicBoolean();
  private Runnable myShutdownTask;

  @TestOnly
  public static LocalHistoryComponent getComponentInstance(Project p) {
    return (LocalHistoryComponent)p.getComponent(LocalHistory.class);
  }

  public static LocalVcs getLocalVcsFor(Project p) {
    return getComponentInstance(p).getLocalVcs();
  }

  public static IdeaGateway getGatewayFor(Project p) {
    return getComponentInstance(p).getGateway();
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

    myShutdownTask = new Runnable() {
      public void run() {
        disposeComponent();
      }
    };
    ShutDownTracker.getInstance().registerShutdownTask(myShutdownTask);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        disposeComponent();
      }
    });

    myStartupManager.registerPreStartupActivity(new Runnable() {
      public void run() {
        init();
      }
    });
  }

  protected void init() {
    initVcs();
    initService();
    isInitialized.set(true);
  }

  protected void initVcs() {
    myStorage = new Storage(getStorageDir());
    myVcs = new LocalVcs(myStorage);
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
    if (!isInitialized()) return;
    myVcs.save();
  }

  public void disposeComponent() {
    if (isInitialized.getAndSet(false)) {
      myVcs.purgeObsoleteAndSave(myConfiguration.PURGE_PERIOD);

      doCloseVcs();
      doCloseService();

      cleanupStorageAfterTestCase();
    }

    ShutDownTracker.getInstance().unregisterShutdownTask(myShutdownTask);
  }

  protected void cleanupStorageAfterTestCase() {
    if (isUnitTestMode()) FileUtil.delete(getStorageDir());
  }

  protected boolean isUnitTestMode() {
    return ApplicationManagerEx.getApplicationEx().isUnitTestMode();
  }

  public void doCloseVcs() {
    myStorage.close();
  }

  protected void doCloseService() {
    myService.shutdown();
  }

  protected boolean isDefaultProject() {
    return myProject.isDefault();
  }

  @Override
  public LocalHistoryAction startAction(String name) {
    if (!isInitialized()) return LocalHistoryAction.NULL;
    return myService.startAction(name);
  }

  @Override
  public Label putUserLabel(String name) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return myVcs.putUserLabel(name);
  }

  @Override
  public Label putUserLabel(VirtualFile f, String name) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return myVcs.putUserLabel(f.getPath(), name);
  }

  @Override
  public Label putSystemLabel(String name, int color) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return myVcs.putSystemLabel(name, color);
  }

  public void registerUnsavedDocuments(VirtualFile file) {
    if (!isInitialized()) return;
    myGateway.registerUnsavedDocuments(myVcs, file);
  }

  @Override
  public byte[] getByteContent(VirtualFile f, FileRevisionTimestampComparator c) {
    if (!isInitialized()) return null;
    if (!isUnderControl(f)) return null;
    return myVcs.getByteContent(f.getPath(), c);
  }

  @Override
  public boolean isUnderControl(VirtualFile f) {
    if (!isInitialized()) return false;
    return myGateway.getFileFilter().isAllowedAndUnderContentRoot(f);
  }

  @Override
  public boolean hasUnavailableContent(VirtualFile f) {
    if (!isInitialized()) return false;
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

  private boolean isInitialized() {
    return isInitialized.get();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Local History";
  }

  public LocalVcs getLocalVcs() {
    return myVcs;
  }

  public IdeaGateway getGateway() {
    return myGateway;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}
