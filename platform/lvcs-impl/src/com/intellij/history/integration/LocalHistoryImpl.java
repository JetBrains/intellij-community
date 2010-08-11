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

import com.intellij.concurrency.JobScheduler;
import com.intellij.history.*;
import com.intellij.history.core.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalHistoryImpl extends LocalHistory implements ApplicationComponent {
  private ChangeList myChangeList;
  private LocalHistoryFacade myVcs;
  private IdeaGateway myGateway;

  private LocalHistoryEventDispatcher myEventDispatcher;

  private final AtomicBoolean isInitialized = new AtomicBoolean();
  private Runnable myShutdownTask;
  private ScheduledFuture<?> myAutoSaveFuture;

  public static LocalHistoryImpl getInstanceImpl() {
    return (LocalHistoryImpl)getInstance();
  }

  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    
    myShutdownTask = new Runnable() {
      public void run() {
        disposeComponent();
      }
    };
    ShutDownTracker.getInstance().registerShutdownTask(myShutdownTask);

    initHistory();
    startAutoSave();
    isInitialized.set(true);
  }

  protected void initHistory() {
    myChangeList = new ChangeList(new ChangeListStorageImpl(getStorageDir()));
    myVcs = new LocalHistoryFacade(myChangeList);

    myGateway = new IdeaGateway();

    myEventDispatcher = new LocalHistoryEventDispatcher(myVcs, myGateway);

    CommandProcessor.getInstance().addCommandListener(myEventDispatcher);

    VirtualFileManager fm = VirtualFileManagerEx.getInstance();
    fm.addVirtualFileListener(myEventDispatcher);
    fm.addVirtualFileManagerListener(myEventDispatcher);
  }

  public File getStorageDir() {
    return new File(getSystemPath(), "LocalHistory");
  }

  protected String getSystemPath() {
    return PathManager.getSystemPath();
  }

  private void startAutoSave() {
    if (ApplicationManagerEx.getApplication().isHeadlessEnvironment()) return;
    myAutoSaveFuture = JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
      public void run() {
        if (!HeavyProcessLatch.INSTANCE.isRunning()) myChangeList.save();
      }
    }, 15000, 15000, TimeUnit.MILLISECONDS);
  }

  public void disposeComponent() {
    if (!isInitialized.getAndSet(false)) return;

    if (myAutoSaveFuture != null) myAutoSaveFuture.cancel(false);

    int period = Registry.intValue("localHistory.daysToKeep") * 1000 * 60 * 60 * 24;

    VirtualFileManager fm = VirtualFileManagerEx.getInstance();
    fm.removeVirtualFileListener(myEventDispatcher);
    fm.removeVirtualFileManagerListener(myEventDispatcher);
    CommandProcessor.getInstance().removeCommandListener(myEventDispatcher);

    myChangeList.purgeObsolete(period);
    myChangeList.close();

    ShutDownTracker.getInstance().unregisterShutdownTask(myShutdownTask);
  }

  @TestOnly
  public void cleanupForNextTest() {
    disposeComponent();
    FileUtil.delete(getStorageDir());
    initComponent();
  }

  @Override
  public LocalHistoryAction startAction(String name) {
    if (!isInitialized()) return LocalHistoryAction.NULL;

    LocalHistoryActionImpl a = new LocalHistoryActionImpl(myEventDispatcher, name);
    a.start();
    return a;
  }

  @Override
  public Label putUserLabel(Project p, String name) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return label(myVcs.putUserLabel(name, getProjectId(p)));
  }

  private String getProjectId(Project p) {
    return p.getLocationHash();
  }

  @Override
  public Label putSystemLabel(Project p, String name, int color) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return label(myVcs.putSystemLabel(name, getProjectId(p), color));
  }

  private Label label(final LabelImpl impl) {
    return new Label() {
      public ByteContent getByteContent(final String path) {
        return ApplicationManager.getApplication().runReadAction(new Computable<ByteContent>() {
          public ByteContent compute() {
            return impl.getByteContent(myGateway.createTransientRootEntry(), path);
          }
        });
      }
    };
  }

  @Override
  public byte[] getByteContent(final VirtualFile f, final FileRevisionTimestampComparator c) {
    if (!isInitialized()) return null;
    if (!myGateway.areContentChangesVersioned(f)) return null;
    return ApplicationManager.getApplication().runReadAction(new Computable<byte[]>() {
      public byte[] compute() {
        return new ByteContentRetriever(myGateway, myVcs, f, c).getResult();
      }
    });
  }

  @Override
  public boolean isUnderControl(VirtualFile f) {
    if (!isInitialized()) return false;
    return myGateway.isVersioned(f);
  }

  private boolean isInitialized() {
    return isInitialized.get();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Local History";
  }

  @Nullable
  public LocalHistoryFacade getFacade() {
    return myVcs;
  }

  @Nullable
  public IdeaGateway getGateway() {
    return myGateway;
  }
}
