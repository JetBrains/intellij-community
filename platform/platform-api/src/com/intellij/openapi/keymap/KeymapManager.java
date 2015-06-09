/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.keymap;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class KeymapManager {
  @NonNls public static final String DEFAULT_IDEA_KEYMAP = "$default";
  @NonNls public static final String MAC_OS_X_KEYMAP = "Mac OS X";
  @NonNls public static final String X_WINDOW_KEYMAP = "Default for XWin";
  @NonNls public static final String MAC_OS_X_10_5_PLUS_KEYMAP = "Mac OS X 10.5+";

  public abstract Keymap getActiveKeymap();

  @Nullable
  public abstract Keymap getKeymap(@NotNull String name);

  public static KeymapManager getInstance(){
    return ApplicationManager.getApplication().getComponent(KeymapManager.class);
  }

  /**
   * @deprecated use {@link KeymapManager#addKeymapManagerListener(KeymapManagerListener, Disposable)} instead
   */
  public abstract void addKeymapManagerListener(@NotNull KeymapManagerListener listener);
  public abstract void addKeymapManagerListener(@NotNull KeymapManagerListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeKeymapManagerListener(@NotNull KeymapManagerListener listener);
}
