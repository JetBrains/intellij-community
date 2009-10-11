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

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;

import java.io.IOException;

public class CreateFileChange extends CreateEntryChange<CreateFileChangeNonAppliedState> {
  public CreateFileChange(int id, String path, Content content, long timestamp, boolean isReadOnly) {
    super(id, path);
    getNonAppliedState().myContent = content;
    getNonAppliedState().myTimestamp = timestamp;
    getNonAppliedState().isReadOnly = isReadOnly;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected CreateFileChangeNonAppliedState createNonAppliedState() {
    return new CreateFileChangeNonAppliedState();
  }

  @Override
  protected IdPath doApplyTo(Entry r, StructuralChangeAppliedState newState) {
    String name = getEntryName();
    String parentPath = getEntryParentPath();

    Entry e = new FileEntry(getNonAppliedState().myId,
                            name,
                            getNonAppliedState().myContent,
                            getNonAppliedState().myTimestamp,
                            getNonAppliedState().isReadOnly);

    return addEntry(r, parentPath, e);
  }
}
