/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.ModificationTracker;

/**
 * Boolean property that implements {@link ModificationTracker}.
 * Not thread safe (external synchronization is required).
 */
public class BooleanTrackableProperty implements ModificationTracker {
  private boolean myValue;
  private long myModificationCount;

  public BooleanTrackableProperty() {
    this(false);
  }

  public BooleanTrackableProperty(boolean value) {
    myValue = value;
  }

  public boolean getValue() {
    return myValue;
  }

  public void setValue(boolean value) {
    boolean oldValue = myValue;
    myValue = value;
    if (value != oldValue) myModificationCount++;
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }
}
