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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public abstract class Change {
  private final long myId;

  protected Change(long id) {
    myId = id;
  }

  protected Change(DataInput in) throws IOException {
    myId = in.readLong();
  }

  public void write(DataOutput out) throws IOException {
    out.writeLong(myId);
  }

  public long getId() {
    return myId;
  }

  public abstract boolean affectsPath(String paths);

  public abstract boolean affectsProject(String projectId);

  public abstract boolean affectsMatching(Pattern pattern);

  public abstract boolean isCreationalFor(String path);

  public abstract List<Content> getContentsToPurge();

  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Change change = (Change)o;

    if (myId != change.myId) return false;

    return true;
  }

  @Override
  public final int hashCode() {
    return (int)(myId ^ (myId >>> 32));
  }
}
