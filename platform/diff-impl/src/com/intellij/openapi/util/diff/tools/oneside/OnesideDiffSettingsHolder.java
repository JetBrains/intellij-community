/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.tools.oneside;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "OnesideDiffSettings",
  storages = {@Storage(
    file = DiffUtil.DIFF_CONFIG)})
public class OnesideDiffSettingsHolder implements PersistentStateComponent<OnesideDiffSettingsHolder.OnesideDiffSettings> {
  public static class OnesideDiffSettings {
    public static final Key<OnesideDiffSettings> KEY = Key.create("OnesideDiffSettings");

    public static final int[] CONTEXT_RANGE_MODES = {1, 2, 4, 8, -1};
    public static final String[] CONTEXT_RANGE_MODE_LABELS = {"1", "2", "4", "8", "All"};

    private int CONTEXT_RANGE = 4;

    public OnesideDiffSettings() {
    }

    public OnesideDiffSettings(int CONTEXT_RANGE) {
      this.CONTEXT_RANGE = CONTEXT_RANGE;
    }

    @NotNull
    private OnesideDiffSettings copy() {
      return new OnesideDiffSettings(CONTEXT_RANGE);
    }

    public int getContextRange() {
      return CONTEXT_RANGE;
    }

    public void setContextRange(int value) {
      CONTEXT_RANGE = value;
    }

    //
    // Impl
    //

    @NotNull
    public static OnesideDiffSettings getSettings() {
      return getInstance().getState().copy();
    }

    @NotNull
    public static OnesideDiffSettings getSettingsDefaults() {
      return getInstance().getState();
    }
  }

  private OnesideDiffSettings myState = new OnesideDiffSettings();

  @NotNull
  public OnesideDiffSettings getState() {
    return myState;
  }

  public void loadState(OnesideDiffSettings state) {
    myState = state;
  }

  public static OnesideDiffSettingsHolder getInstance() {
    return ServiceManager.getService(OnesideDiffSettingsHolder.class);
  }
}

