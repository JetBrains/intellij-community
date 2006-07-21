/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.startup;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.project.Project;

public abstract class StartupManager {
  public static StartupManager getInstance(Project project) {
    return project.getComponent(StartupManager.class);
  }

  public abstract void registerStartupActivity(Runnable runnable);

  public abstract void registerPostStartupActivity(Runnable runnable);

  public abstract void runWhenProjectIsInitialized(Runnable runnable);

  public abstract FileSystemSynchronizer getFileSystemSynchronizer();
}
