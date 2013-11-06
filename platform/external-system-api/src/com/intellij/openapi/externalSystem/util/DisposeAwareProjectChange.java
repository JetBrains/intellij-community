/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.components.ComponentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 11/6/13
 */
public abstract class DisposeAwareProjectChange implements Runnable {
  private final ComponentManager myComponentManager;

  protected DisposeAwareProjectChange(@NotNull ComponentManager componentManager) {
    myComponentManager = componentManager;
  }

  public abstract void execute();

  public final void run() {
    if (!myComponentManager.isDisposed()) {
      execute();
    }
  }
}
