/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.intellij.ide.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author peter
 */
public class SyncSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.SyncSession");
  private final LinkedHashSet<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();
  private final CacheUpdater[] myUpdaters;
  private final CacheUpdateSets myIndexingSets;
  private final CacheUpdateSets myContentSets;

  public SyncSession(List<CacheUpdater> updaters) {
    myUpdaters = updaters.toArray(new CacheUpdater[updaters.size()]);

    List<List<VirtualFile>> updateSets = new ArrayList<List<VirtualFile>>();
    for (CacheUpdater updater : updaters) {
      try {
        List<VirtualFile> updaterFiles = Arrays.asList(updater.queryNeededFiles());
        updateSets.add(updaterFiles);
        myFilesToUpdate.addAll(updaterFiles);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    myContentSets = new CacheUpdateSets(updateSets);
    myIndexingSets = new CacheUpdateSets(updateSets);
  }

  public LinkedHashSet<VirtualFile> getFilesToUpdate() {
    return myFilesToUpdate;
  }

  void processFile(FileContent content) {
    for (int i = 0; i < myUpdaters.length; i++) {
      CacheUpdater updater = myUpdaters[i];
      if (updater != null && myIndexingSets.remove(i, content.getVirtualFile())) {
        try {
          updater.processFile(content);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
        if (myIndexingSets.isDoneForegroundly(i)) {
          try {
            updater.updatingDone();
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
          myUpdaters[i] = null; //not to call updatingDone second time in updatingDone
        }
      }
    }

  }

  void updatingDone() {
    for (int i = 0; i < myUpdaters.length; i++) {
      CacheUpdater updater = myUpdaters[i];
      try {
        if (updater != null && myIndexingSets.isDoneForegroundly(i)) {
          updater.updatingDone();
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    myFilesToUpdate.clear();
  }

  public boolean shouldIndex(VirtualFile file) {
    return myIndexingSets.contains(file);
  }

  public CacheUpdater[] getUpdaters() {
    return myUpdaters;
  }

  @Nullable
  public List<VirtualFile> backgrounded(int updaterIndex) {
    final List<VirtualFile> remaining = myIndexingSets.backgrounded(updaterIndex);
    myContentSets.backgrounded(updaterIndex); //not to load the content of files needed only for the backgrounded CacheUpdater
    return remaining;
  }

  @Nullable
  public List<VirtualFile> getRemainingFiles(int updaterIndex) {
    return myIndexingSets.getRemainingFiles(updaterIndex);
  }

  public void canceled() {
    for (CacheUpdater updater : myUpdaters) {
      if (updater != null) {
        updater.canceled();
      }
    }
  }
}
