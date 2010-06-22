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
import com.intellij.openapi.util.Pair;

import java.util.List;

public abstract class ChangeSetsProcessor {
  protected String myPath;

  public ChangeSetsProcessor(String path) {
    myPath = path;
  }

  protected void process() {
    Pair<String, List<ChangeSet>> pathAndChanges = collectChanges();

    List<ChangeSet> changes = pathAndChanges.second;
    if (changes.isEmpty()) {
      nothingToVisit();
      return;
    }

    for (ChangeSet c : changes) {
      visit(c);
    }
  }

  protected abstract Pair<String, List<ChangeSet>> collectChanges();

  protected abstract void nothingToVisit();

  protected abstract void visit(ChangeSet changeSet);
}
