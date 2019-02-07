// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manager of listeners for notifications about activation and deactivation of the
 * IDEA window.
 *
 * @since 5.0.2
 */
public abstract class FrameStateManager {
  /**
   * Returns the global {@code FrameStateManager} instance.
   *
   * @return the component instance.
   */
  public static FrameStateManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FrameStateManager.class);
  }

  /**
   * @deprecated Use message bus {@link FrameStateListener#TOPIC}
   */
  @Deprecated
  public abstract void addListener(@NotNull FrameStateListener listener);

  /**
   * @deprecated Use message bus {@link FrameStateListener#TOPIC}
   */
  @Deprecated
  public abstract void addListener(@NotNull FrameStateListener listener, @Nullable Disposable disposable);

  /**
   * Removes a listener which is called when the IDEA window is activated or deactivated.
   *
   * @param listener the listener instance.
   */
  public abstract void removeListener(@NotNull FrameStateListener listener);


  /**
   * @return action callback for application's active state
   */
  public abstract ActionCallback getApplicationActive();
}
