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

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.tree.Entry;

import java.util.List;

public abstract class ChangeSetsProcessor {
  protected LocalVcs myVcs;
  protected String myPath;
  protected Entry myEntry;

  public ChangeSetsProcessor(LocalVcs vcs, String path) {
    myVcs = vcs;
    myPath = path;
    myEntry = myVcs.getEntry(path);
  }

  protected void process() {
    List<Change> changes = collectChanges();

    if (changes.isEmpty()) {
      nothingToVisit();
      return;
    }

    for (Change c : changes) {
      if (c.isLabel()) {
        visitLabel(c);
      }
      else {
        visitRegular(c);
      }
    }

    Change lastChange = changes.get(changes.size() - 1);
    if (!lastChange.isLabel() && !lastChange.isCreationalFor(myEntry)) {
      visitFirstAvailableNonCreational(lastChange);
    }
  }

  protected abstract List<Change> collectChanges();

  protected abstract void nothingToVisit();

  protected abstract void visitLabel(Change c);

  protected abstract void visitRegular(Change c);

  protected abstract void visitFirstAvailableNonCreational(Change c);
}
