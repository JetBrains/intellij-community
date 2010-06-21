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
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class Revision {
  @Nullable
  public String getLabel() {
    return null;
  }

  public int getLabelColor() {
    return -1;
  }

  public abstract long getTimestamp();

  @Nullable
  public Long getChangeSetId() {
    return null;
  }

  @Nullable
  public String getChangeSetName() {
    return null;
  }

  public boolean isLabel() {
    return getAffectedFileNames().first.isEmpty();
  }

  public Pair<List<String>, Integer> getAffectedFileNames() {
    return Pair.create(Collections.<String>emptyList(), 0);
  }

  public abstract Entry findEntry();

  public List<Difference> getDifferencesWith(Revision right) {
    return Entry.getDifferencesBetween(findEntry(), right.findEntry());
  }
}
