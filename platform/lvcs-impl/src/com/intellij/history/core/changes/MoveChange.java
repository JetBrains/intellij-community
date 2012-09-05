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
import com.intellij.history.core.StreamUtil;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MoveChange extends StructuralChange {
  private final String myOldPath;

  public MoveChange(long id, String path, String oldParent) {
    super(id, path);
    myOldPath = Paths.appended(oldParent, Paths.getNameOf(path));
  }

  public MoveChange(DataInput in) throws IOException {
    super(in);
    myOldPath = StreamUtil.readString(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    StreamUtil.writeString(out, myOldPath);
  }

  public String getOldPath() {
    return myOldPath;
  }

  public String getOldParent() {
    return Paths.getParentOf(myOldPath);
  }

  @Override
  public void revertOn(RootEntry root, boolean warnOnFileNotFound) {
    Entry e = root.findEntry(myPath);
    if (e == null) {
      cannotRevert(myPath, warnOnFileNotFound);
      return;
    }
    removeEntry(e);

    Entry oldParent = root.findEntry(getOldParent());
    if (oldParent == null) {
      cannotRevert(getOldParent(), warnOnFileNotFound);
      return;
    }

    oldParent.addChild(e);
  }

  @Override
  protected String[] getAffectedPaths() {
    return new String[]{myPath, myOldPath};
  }

  @Override
  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
