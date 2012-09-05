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
import com.intellij.history.utils.LocalHistoryLog;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public abstract class StructuralChange extends Change {
  protected final String myPath;

  protected StructuralChange(long id, String path) {
    super(id);
    myPath = path;
  }

  protected StructuralChange(DataInput in) throws IOException {
    super(in);
    myPath = StreamUtil.readString(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    StreamUtil.writeString(out, myPath);
  }

  protected void removeEntry(Entry e) {
    e.getParent().removeChild(e);
  }

  public String getPath() {
    return myPath;
  }

  public String getOldPath() {
    return myPath;
  }

  public abstract void revertOn(RootEntry root, boolean warnOnFileNotFound);

  protected void cannotRevert(String path, boolean warnOnFileNotFound) {
    if (warnOnFileNotFound) {
      LocalHistoryLog.LOG.warn("cannot revert " + getClass().getSimpleName() + "->file not found: " + path);
    }
  }

  public String revertPath(String path) {
    if (Paths.equals(getPath(), getOldPath())) return path;

    String relative = Paths.relativeIfUnder(path, myPath);
    if (relative == null) return path;
    if (relative.isEmpty()) return getOldPath();
    return Paths.appended(getOldPath(), relative);
  }

  @Override
  public boolean affectsPath(String path) {
    for (String each : getAffectedPaths()) {
      if (Paths.isParentOrChild(each, path)) return true;
    }
    return false;
  }

  @Override
  public boolean affectsProject(String projectId) {
    return false;
  }

  @Override
  public boolean affectsMatching(Pattern pattern) {
    for (String each : getAffectedPaths()) {
      if ( pattern.matcher(Paths.getNameOf(each)).matches()) return true;
    }
    return false;
  }

  protected String[] getAffectedPaths() {
    return new String[]{myPath};
  }

  @Override
  public boolean isCreationalFor(String path) {
    return false;
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }

  public String toString() {
    return getClass().getSimpleName() + ": " + myPath;
  }
}
