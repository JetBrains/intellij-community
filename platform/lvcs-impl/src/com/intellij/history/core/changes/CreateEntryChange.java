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

package com.intellij.history.core.changes;

import com.intellij.history.core.Paths;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;

import java.io.DataInput;
import java.io.IOException;

public abstract class CreateEntryChange extends StructuralChange {
  public CreateEntryChange(long id, String path) {
    super(id, path);
  }

  public CreateEntryChange(DataInput in) throws IOException {
    super(in);
  }

  @Override
  public void revertOn(RootEntry root, boolean warnOnFileNotFound) {
    Entry e = root.findEntry(myPath);
    if (e == null) {
      cannotRevert(myPath, warnOnFileNotFound);
      return;
    }
    removeEntry(e);
  }

  @Override
  public boolean isCreationalFor(String path) {
    return Paths.equals(myPath, path);
  }

  @Override
  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
