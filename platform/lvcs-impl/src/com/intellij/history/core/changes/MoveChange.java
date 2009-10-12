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
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class MoveChange extends StructuralChange<MoveChangeNonAppliedState, MoveChangeAppliedState> {
  public MoveChange(String path, String newParentPath) {
    super(path);
    getNonAppliedState().myNewParentPath = newParentPath;
  }

  public MoveChange(Stream s) throws IOException {
    super(s);
    getAppliedState().myTargetIdPath = s.readIdPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeIdPath(getAppliedState().myTargetIdPath);
  }

  @Override
  protected MoveChangeAppliedState createAppliedState() {
    return new MoveChangeAppliedState();
  }

  @Override
  protected MoveChangeNonAppliedState createNonAppliedState() {
    return new MoveChangeNonAppliedState();
  }

  @Override
  protected IdPath doApplyTo(Entry r, MoveChangeAppliedState newState) {
    Entry e = r.getEntry(getPath());
    IdPath firstIdPath = e.getIdPath();

    removeEntry(e);

    Entry newParent = r.getEntry(getNonAppliedState().myNewParentPath);
    newParent.addChild(e);

    newState.myTargetIdPath = e.getIdPath();

    return firstIdPath;
  }

  @Override
  public void doRevertOn(Entry root) {
    Entry e = getEntry(root);
    removeEntry(e);

    Entry oldParent = getOldParent(root);
    oldParent.addChild(e);
  }

  @Override
  public boolean canRevertOn(Entry r) {
    return hasNoSuchEntry(getOldParent(r), getEntry(r).getName());
  }

  private Entry getEntry(Entry r) {
    return r.getEntry(getAppliedState().myTargetIdPath);
  }

  private Entry getOldParent(Entry r) {
    return r.getEntry(getAffectedIdPath().getParent());
  }

  @Override
  public IdPath[] getAffectedIdPaths() {
    return new IdPath[]{getAffectedIdPath(), getAppliedState().myTargetIdPath};
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
