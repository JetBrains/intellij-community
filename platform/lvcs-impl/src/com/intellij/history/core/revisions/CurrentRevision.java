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

package com.intellij.history.core.revisions;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.util.Clock;

import java.util.Objects;

public final class CurrentRevision extends Revision {
  private final RootEntry myRoot;
  private final String myPath;

  public CurrentRevision(RootEntry root, String path) {
    myRoot = root;
    myPath = path;
  }

  @Override
  public long getTimestamp() {
    Entry entry = findEntry();
    return entry != null ? entry.getTimestamp() : Clock.getTime();
  }

  @Override
  public Entry findEntry() {
    return myRoot.findEntry(myPath);
  }

  @Override
  public RootEntry getRoot() {
    return myRoot;
  }

  @Override
  public boolean isOldContentUsed() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CurrentRevision that)) return false;
    return Objects.equals(myRoot, that.myRoot) && Objects.equals(myPath, that.myPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRoot, myPath);
  }
}
