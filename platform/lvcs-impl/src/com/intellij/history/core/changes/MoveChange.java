// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core.changes;

import com.intellij.history.core.DataStreamUtil;
import com.intellij.history.core.Paths;
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
    myOldPath = DataStreamUtil.readString(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    DataStreamUtil.writeString(out, myOldPath);
  }

  @Override
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
