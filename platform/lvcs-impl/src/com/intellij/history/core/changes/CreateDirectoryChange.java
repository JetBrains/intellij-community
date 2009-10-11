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

import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.IdPath;

import java.io.IOException;

public class CreateDirectoryChange extends CreateEntryChange<CreateEntryChangeNonAppliedState> {
  public CreateDirectoryChange(int id, String path) {
    super(id, path);
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r, StructuralChangeAppliedState newState) {
    // todo messsssss!!!! should introduce createRoot method instead?
    // todo and simplify addEntry method too?
    String name = getEntryName();
    String parentPath = getEntryParentPath();

    if (parentPath == null || !r.hasEntry(parentPath)) { // is it supposed to be a root?
      parentPath = null;
      name = getPath();
    }

    DirectoryEntry e = new DirectoryEntry(getNonAppliedState().myId, name);
    return addEntry(r, parentPath, e);
  }
}
