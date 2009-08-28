/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ide.startup.CacheUpdater;
import org.jetbrains.annotations.Nullable;

public abstract class RefreshQueue {
  public static RefreshQueue getInstance() {
    return ServiceManager.getService(RefreshQueue.class);
  }

  public abstract RefreshSession createSession(boolean async, boolean recursive, @Nullable Runnable finishRunnable);

  public final void refresh(boolean async, boolean recursive, @Nullable Runnable finishRunnable, VirtualFile... files) {
    RefreshSession session = createSession(async, recursive, finishRunnable);
    session.addAllFiles(files);
    session.launch();
  }

  public abstract void registerRefreshUpdater(CacheUpdater updater);
  public abstract void unregisterRefreshUpdater(CacheUpdater updater);

  public abstract void processSingleEvent(VFileEvent event);
}
