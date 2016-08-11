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

import com.intellij.history.core.Content;
import com.intellij.history.core.Paths;
import com.intellij.history.core.StreamUtil;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeleteChange extends StructuralChange {
  private final Entry myDeletedEntry;

  public DeleteChange(long id, String path, Entry deletedEntry) {
    super(id, path);
    myDeletedEntry = deletedEntry;
  }

  public DeleteChange(DataInput in) throws IOException {
    super(in);
    myDeletedEntry = StreamUtil.readEntry(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    StreamUtil.writeEntry(out, myDeletedEntry);
  }

  public Entry getDeletedEntry() {
    return myDeletedEntry;
  }

  @Override
  public void revertOn(RootEntry root, boolean warnOnFileNotFound) {
    String parentPath = Paths.getParentOf(myPath);
    Entry parent = root.findEntry(parentPath);
    if (parent == null) {
      cannotRevert(parentPath, warnOnFileNotFound);
      return;
    }
    parent.addChild(myDeletedEntry.copy());
  }

  public boolean isDeletionOf(String p) {
    String relative = Paths.relativeIfUnder(p, myPath);
    if (relative == null) return false;
    return myDeletedEntry.hasEntry(relative);
  }

  @Override
  public List<Content> getContentsToPurge() {
    List<Content> result = new ArrayList<>();
    collectContentsRecursively(myDeletedEntry, result);
    return result;
  }

  private void collectContentsRecursively(Entry e, List<Content> result) {
    if (e.isDirectory()) {
      for (Entry child : e.getChildren()) {
        collectContentsRecursively(child, result);
      }
    }
    else {
      result.add(e.getContent());
    }
  }

  @Override
  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
