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

import com.intellij.history.LocalHistoryAction;
import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.core.LocalVcs;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;

public class LocalHistoryService {
  private final LocalVcs myVcs;
  private final IdeaGateway myGateway;
  private final LocalHistoryConfiguration myConfiguration;
  // todo get rid of all this managers...
  private final StartupManager myStartupManager;
  private final ProjectRootManagerEx myRootManager;
  private final VirtualFileManagerEx myFileManager;
  private final CommandProcessor myCommandProcessor;

  private CacheUpdater myCacheUpdater;
  private EventDispatcher myEventDispatcher;

  public LocalHistoryService(LocalVcs vcs,
                             IdeaGateway gw,
                             LocalHistoryConfiguration c,
                             StartupManager sm,
                             ProjectRootManagerEx projectRootManager,
                             VirtualFileManagerEx fm,
                             CommandProcessor cp) {
    myVcs = vcs;
    myGateway = gw;
    myConfiguration = c;
    myStartupManager = sm;
    myRootManager = projectRootManager;
    myFileManager = fm;
    myCommandProcessor = cp;

    registerCacheUpdaters();
    registerListeners();
  }

  private void registerCacheUpdaters() {
    FileSystemSynchronizer fs = myStartupManager.getFileSystemSynchronizer();
    fs.registerCacheUpdater(new StartupChangeUpdater());

    myCacheUpdater = new CacheUpdaterAdaptor();
    myRootManager.registerChangeUpdater(myCacheUpdater);
  }

  private void registerListeners() {
    myEventDispatcher = new EventDispatcher(myVcs, myGateway);

    myCommandProcessor.addCommandListener(myEventDispatcher);
    myFileManager.addVirtualFileListener(myEventDispatcher);
    myFileManager.addVirtualFileManagerListener(myEventDispatcher);
    myFileManager.registerRefreshUpdater(myEventDispatcher);
  }

  public void shutdown() {
    myFileManager.unregisterRefreshUpdater(myEventDispatcher);
    myFileManager.removeVirtualFileListener(myEventDispatcher);
    myFileManager.removeVirtualFileManagerListener(myEventDispatcher);
    myCommandProcessor.removeCommandListener(myEventDispatcher);

    myRootManager.unregisterChangeUpdater(myCacheUpdater);
  }

  public LocalHistoryAction startAction(String name) {
    LocalHistoryActionImpl a = new LocalHistoryActionImpl(myEventDispatcher, name);
    a.start();
    return a;
  }

  public class StartupChangeUpdater extends CacheUpdaterAdaptor {
    @Override
    public void updatingDone() {
      super.updatingDone();
      if (myConfiguration.ADD_LABEL_ON_PROJECT_OPEN) {
        myVcs.putSystemLabel(LocalHistoryBundle.message("system.label.project.open"), -1);
      }
    }
  }

  // todo only needed because we should calculate content roots each time
  // todo try to remove this class
  private class CacheUpdaterAdaptor implements CacheUpdater {
    private Updater myUpdater;

    public VirtualFile[] queryNeededFiles() {
      myUpdater = new Updater(myVcs, myGateway);
      return myUpdater.queryNeededFiles();
    }

    public void processFile(FileContent c) {
      myUpdater.processFile(c);
    }

    public void updatingDone() {
      myUpdater.updatingDone();
    }

    public void canceled() {
      myUpdater.canceled();
    }
  }
}
