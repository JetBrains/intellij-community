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

package com.intellij.history.integration.stubs;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.FileSystemSynchronizer;
import org.jetbrains.annotations.NotNull;

public class StubStartupManagerEx extends StartupManagerEx {
  public void registerStartupActivity(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  public void registerPostStartupActivity(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  public void runWhenProjectIsInitialized(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  public FileSystemSynchronizer getFileSystemSynchronizer() {
    throw new UnsupportedOperationException();
  }

  public boolean startupActivityRunning() {
    throw new UnsupportedOperationException();
  }

  public boolean startupActivityPassed() {
    throw new UnsupportedOperationException();
  }

  public boolean postStartupActivityPassed() {
    throw new UnsupportedOperationException();
  }

  public void registerPreStartupActivity(@NotNull Runnable runnable) // should be used only to register to FileSystemSynchronizer!
  {
    throw new UnsupportedOperationException();
  }
}
