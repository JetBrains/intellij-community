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

package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.revisions.ChangeRevision;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.RootEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RevisionsCollector {
  public static @NotNull List<Revision> collect(LocalHistoryFacade facade,
                                                RootEntry rootEntry,
                                                @NotNull String path,
                                                String projectId,
                                                @Nullable String pattern) {
    return collect(facade, rootEntry, path, projectId, pattern, true);
  }

  public static @NotNull List<Revision> collect(LocalHistoryFacade facade,
                                                RootEntry rootEntry,
                                                @NotNull String path,
                                                String projectId,
                                                @Nullable String pattern,
                                                boolean before) {
    List<Revision> result = new ArrayList<>();
    // todo optimize to not collect all change sets + do not process changes twice
    ChangeCollectingVisitor v = new ChangeCollectingVisitor(path, projectId, pattern);
    facade.accept(v);
    for (ChangeSet c : v.getChanges()) {
      result.add(new ChangeRevision(facade, rootEntry, path, c, before));
    }
    return result;
  }
}
