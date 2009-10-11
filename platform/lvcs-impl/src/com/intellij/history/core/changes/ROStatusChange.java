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
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.IdPath;

import java.io.IOException;

public class ROStatusChange extends StructuralChange<ROStatusChangeNonAppliedState, ROStatusChangeAppliedState> {
  public ROStatusChange(String path, boolean isReadOnly) {
    super(path);
    getNonAppliedState().myNewStatus = isReadOnly;
  }

  public ROStatusChange(Stream s) throws IOException {
    super(s);
    getAppliedState().myOldStatus = s.readBoolean();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeBoolean(getAppliedState().myOldStatus);
  }

  @Override
  protected ROStatusChangeAppliedState createAppliedState() {
    return new ROStatusChangeAppliedState();
  }

  @Override
  protected ROStatusChangeNonAppliedState createNonAppliedState() {
    return new ROStatusChangeNonAppliedState();
  }

  public boolean getOldStatus() {
    return getAppliedState().myOldStatus;
  }

  @Override
  protected IdPath doApplyTo(Entry r, ROStatusChangeAppliedState newState) {
    Entry e = r.getEntry(getPath());
    newState.myOldStatus = e.isReadOnly();
    e.setReadOnly(getNonAppliedState().myNewStatus);

    return e.getIdPath();
  }

  @Override
  public void doRevertOn(Entry root) {
    getEntry(root).setReadOnly(getAppliedState().myOldStatus);
  }

  private Entry getEntry(Entry r) {
    return r.getEntry(getAffectedIdPath());
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
