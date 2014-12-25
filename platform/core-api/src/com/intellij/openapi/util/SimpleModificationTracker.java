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
package com.intellij.openapi.util;

import com.intellij.Patches;
import com.intellij.util.xmlb.annotations.Transient;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Created by Max Medvedev on 28/03/14
 */
@Transient
public class SimpleModificationTracker implements ModificationTracker {
  static {
    // field made public to workaround bug in JDK7 when AtomicIntegerFieldUpdater can't be created for private field, even from within its own class
    // fixed in JDK8
    assert Patches.JDK_BUG_ID_7103570;
  }

  public volatile int myCounter;

  @Override
  public long getModificationCount() {
    return myCounter;
  }

  private static final AtomicIntegerFieldUpdater<SimpleModificationTracker> UPDATER = AtomicIntegerFieldUpdater.newUpdater(SimpleModificationTracker.class, "myCounter");

  public void incModificationCount() {
    UPDATER.incrementAndGet(this);
  }
}
