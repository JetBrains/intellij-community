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
import com.intellij.history.core.*;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalHistoryImpl extends LocalHistory implements ApplicationComponent {
  private ChangeList myChangeList;
  private LocalHistoryFacade myVcs;
  private IdeaGateway myGateway;

  private LocalHistoryEventDispatcher myEventDispatcher;

  private final AtomicBoolean isInitialized = new AtomicBoolean();
  private Runnable myShutdownTask;

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
    isInitialized.set(true);
  }

  protected void initHistory() {
    ChangeListStorage storage;
    try {
      storage = new ChangeListStorageImpl(getStorageDir());
    }
    catch (Throwable e) {
      LocalHistoryLog.LOG.warn("cannot create storage, in-memory  implementation will be used", e);
      storage = new InMemoryChangeListStorage();
    }
    myChangeList = new ChangeList(storage);
    myVcs = new LocalHistoryFacade(myChangeList);

    myGateway = new IdeaGateway();

    myEventDispatcher = new LocalHistoryEventDispatcher(myVcs, myGateway);

    CommandProcessor.getInstance().addCommandListener(myEventDispatcher);

    VirtualFileManager fm = VirtualFileManager.getInstance();
    fm.addVirtualFileListener(myEventDispatcher);
    fm.addVirtualFileManagerListener(myEventDispatcher);

    if (ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          validateStorage();
        }
      });
    }
  }

  private void validateStorage() {
    if (ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode()) {
      LocalHistoryLog.LOG.info("Checking local history storage...");
      try {
        long before = Clock.getTime();
        myVcs.getChangeListInTests().getChangesInTests();
        LocalHistoryLog.LOG.info("Local history storage seems to be ok (took " + ((Clock.getTime() - before) / 1000) + " sec)");
      }
      catch (Exception e) {
        LocalHistoryLog.LOG.error(e);
      }
    }
  }

  public File getStorageDir() {
    return new File(getSystemPath(), "LocalHistory");
  }

  protected String getSystemPath() {
    return PathManager.getSystemPath();
  }

  public void disposeComponent() {
    if (!isInitialized.getAndSet(false)) return;

    int period = Registry.intValue("localHistory.daysToKeep") * 1000 * 60 * 60 * 24;

    VirtualFileManager fm = VirtualFileManager.getInstance();
    fm.removeVirtualFileListener(myEventDispatcher);
    fm.removeVirtualFileManagerListener(myEventDispatcher);
    CommandProcessor.getInstance().removeCommandListener(myEventDispatcher);


    validateStorage();
    LocalHistoryLog.LOG.info("Purging local history...");
    myChangeList.purgeObsolete(period);
    validateStorage();

    myChangeList.close();
    LocalHistoryLog.LOG.info("Local history storage successfully closed.");

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
  public Label putUserLabel(Project p, @NotNull String name) {
    if (!isInitialized()) return Label.NULL_INSTANCE;
    myGateway.registerUnsavedDocuments(myVcs);
    return label(myVcs.putUserLabel(name, getProjectId(p)));
  }

  private String getProjectId(Project p) {
    return p.getLocationHash();
  }

  @Override
  public Label putSystemLabel(Project p, @NotNull String name, int color) {
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

  @Nullable
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
