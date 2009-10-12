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

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.tree.Entry;

public class LabeledRevision extends RevisionAfterChange {
  public LabeledRevision(Entry e, Entry r, ChangeList cl, Change c) {
    super(e, r, cl, c);
  }

  @Override
  public String getName() {
    return myChange.getName();
  }

  @Override
  public String getCauseChangeName() {
    return null;
  }

  @Override
  public boolean isImportant() {
    return !myChange.isSystemLabel();
  }
}