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
package com.intellij.openapi.util;

import com.intellij.util.xmlb.annotations.Transient;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * @author Max Medvedev
 * @since 28.03.2014
 */
@Transient
public class SimpleModificationTracker implements ModificationTracker {
  private static final AtomicLongFieldUpdater<SimpleModificationTracker> UPDATER =
    AtomicLongFieldUpdater.newUpdater(SimpleModificationTracker.class, "myCounter");

  @SuppressWarnings("unused")
  private volatile long myCounter;

  @Override
  public long getModificationCount() {
    return myCounter;
  }

  public void incModificationCount() {
    UPDATER.incrementAndGet(this);
  }
}