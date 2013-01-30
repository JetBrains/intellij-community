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
package com.intellij.ide;

/**
 * Listener for receiving notifications when the IDEA window is activated or deactivated.
 *
 * @since 5.0.2
 * @see FrameStateManager#addListener(FrameStateListener)
 * @see FrameStateManager#removeListener(FrameStateListener)
 */
public interface FrameStateListener {
  /**
   * Called when the IDEA window is deactivated.
   */
  void onFrameDeactivated();

  /**
   * Called when the IDEA window is activated.
   */
  void onFrameActivated();

  abstract class Adapter implements FrameStateListener {
    @Override
    public void onFrameDeactivated() { }

    @Override
    public void onFrameActivated() { }
  }
}
