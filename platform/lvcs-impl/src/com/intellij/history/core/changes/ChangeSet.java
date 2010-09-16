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

import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.StreamUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeSet {
  private final long myId;
  private String myName;
  private final long myTimestamp;
  private final List<Change> myChanges;

  public ChangeSet(long id, long timestamp) {
    myId = id;
    myTimestamp = timestamp;
    myChanges = new ArrayList<Change>();
  }

  public ChangeSet(DataInput in) throws IOException {
    myId = in.readLong();
    myName = StreamUtil.readStringOrNull(in);
    myTimestamp = in.readLong();

    int count = in.readInt();
    myChanges = new ArrayList<Change>(count);
    while (count-- > 0) {
      myChanges.add(StreamUtil.readChange(in));
    }
  }

  public void write(DataOutput out) throws IOException {
    out.writeLong(myId);
    StreamUtil.writeStringOrNull(out, myName);
    out.writeLong(myTimestamp);

    out.writeInt(myChanges.size());
    for (Change c : myChanges) {
      StreamUtil.writeChange(out, c);
    }
  }

  public void setName(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  @Nullable
  public String getLabel() {
    for (Change each : myChanges) {
      if (each instanceof PutLabelChange) {
       return ((PutLabelChange)each).getName();
      }
    }
    return null;
  }

  public int getLabelColor() {
    for (Change each : myChanges) {
      if (each instanceof PutSystemLabelChange) {
       return ((PutSystemLabelChange)each).getColor();
      }
    }
    return -1;
  }

  public void addChange(Change c) {
    myChanges.add(c);
  }

  public List<Change> getChanges() {
    return myChanges;
  }

  public boolean isEmpty() {
    return myChanges.isEmpty();
  }

  public boolean affectsPath(String paths) {
    for (Change c : myChanges) {
      if (c.affectsPath(paths)) return true;
    }
    return false;
  }

  public boolean isCreationalFor(String path) {
    for (Change c : myChanges) {
      if (c.isCreationalFor(path)) return true;
    }
    return false;
  }

  public List<Content> getContentsToPurge() {
    List<Content> result = new ArrayList<Content>();
    for (Change c : myChanges) {
      result.addAll(c.getContentsToPurge());
    }
    return result;
  }

  public boolean isContentChangeOnly() {
    return myChanges.size() == 1 && getFirstChange() instanceof ContentChange;
  }

  public boolean isLabelOnly() {
    return myChanges.size() == 1 && getFirstChange() instanceof PutLabelChange;
  }

  public Change getFirstChange() {
    return myChanges.get(0);
  }

  public Change getLastChange() {
    return myChanges.get(myChanges.size() - 1);
  }

  public List<String> getAffectedPaths() {
    List<String> result = new SmartList<String>();
    for (Change each : myChanges) {
      if (each instanceof StructuralChange) {
        result.add(((StructuralChange)each).getPath());
      }
    }
    return result;
  }

  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.begin(this);
    for (Change c : ContainerUtil.iterateBackward(myChanges)) {
      c.accept(v);
    }
    v.end(this);
  }

  public String toString() {
    return myChanges.toString();
  }

  public long getId() {
    return myId;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChangeSet change = (ChangeSet)o;

    if (myId != change.myId) return false;

    return true;
  }

  @Override
  public final int hashCode() {
    return (int)(myId ^ (myId >>> 32));
  }
}
