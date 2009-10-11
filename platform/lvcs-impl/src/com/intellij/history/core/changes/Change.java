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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public abstract class Change {
  public abstract void write(Stream s) throws IOException;

  public String getName() {
    throw new UnsupportedOperationException();
  }

  public long getTimestamp() {
    throw new UnsupportedOperationException();
  }

  public List<Change> getChanges() {
    return Collections.singletonList(this);
  }

  public abstract void applyTo(Entry r);

  public void revertOn(Entry r) {
    revertOnUpTo(r, null, false);
  }

  public boolean revertOnUpTo(Entry r, Change upTo, boolean revertTargetChange) {
    if (!revertTargetChange && this == upTo) return false;
    doRevertOn(r);
    return this != upTo;
  }

  protected abstract void doRevertOn(Entry root);

  public boolean canRevertOn(Entry r) {
    return true;
  }

  public boolean affects(Entry e) {
    return affects(e.getIdPath());
  }

  protected abstract boolean affects(IdPath... pp);

  public boolean affectsSameAs(List<Change> cc) {
    return false;
  }

  public abstract boolean affectsOnlyInside(Entry e);

  public abstract boolean isCreationalFor(Entry e);

  public abstract List<Content> getContentsToPurge();

  public boolean isLabel() {
    return false;
  }

  public boolean isSystemLabel() {
    return false;
  }

  public boolean isFileContentChange() {
    return false;
  }

  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
  }
}
