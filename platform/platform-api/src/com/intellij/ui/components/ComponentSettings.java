/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.components;

/**
 * IDE-agnostic component settings.
 */
public class ComponentSettings {
  private boolean mySmoothScrollingEnabled;
  private boolean myRemoteDesktopConnected;

  private static final ComponentSettings ourInstance = new ComponentSettings();

  public static ComponentSettings getInstance() {
    return ourInstance;
  }

  public boolean isSmoothScrollingEligible() {
    return mySmoothScrollingEnabled && !myRemoteDesktopConnected;
  }

  public void setSmoothScrollingEnabled(boolean enabled) {
    mySmoothScrollingEnabled = enabled;
  }

  public void setRemoteDesktopConnected(boolean connected) {
    myRemoteDesktopConnected = connected;
  }
}
