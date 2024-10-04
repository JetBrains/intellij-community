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

package com.intellij.history.core.tree;

import com.intellij.history.core.Content;
import com.intellij.history.core.DataStreamUtil;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static java.lang.String.format;

public abstract class Entry {
  private int myNameId;
  private int myNameHash; // case insensitive
  private DirectoryEntry myParent;

  public Entry(@NonNls String name) {
    this(toNameId(name), calcNameHash(name));
  }

  public Entry(int nameId) {
    this(nameId, calcNameHash(fromNameId(nameId)));
  }

  private Entry(int nameId, int nameHash) {
    myNameId = nameId;
    myNameHash = nameHash;
  }

  private static final int NULL_NAME_ID = -1;
  private static final int EMPTY_NAME_ID = 0;

  protected static int toNameId(@NonNls String name) {
    if (name == null) return NULL_NAME_ID;
    if (name.isEmpty()) return EMPTY_NAME_ID;
    return FSRecords.getInstance().getNameId(name);
  }

  private static CharSequence fromNameId(int nameId) {
    if (nameId == NULL_NAME_ID) return null;
    if (nameId == EMPTY_NAME_ID) return "";
    return FSRecords.getInstance().getNameByNameId(nameId);
  }

  public Entry(DataInput in) throws IOException {
    String name = DataStreamUtil.readString(in);
    myNameId = toNameId(name);
    myNameHash = calcNameHash(name);
  }

  public void write(DataOutput out) throws IOException {
    DataStreamUtil.writeString(out, getName());
  }

  public @NlsSafe String getName() {
    CharSequence sequence = fromNameId(myNameId);
    if (sequence != null && !(sequence instanceof String)) {
      return sequence.toString();
    }
    return (String)sequence;
  }

  public @NlsSafe CharSequence getNameSequence() {
    return fromNameId(myNameId);
  }

  public int getNameId() {
    return myNameId;
  }

  public int getNameHash() {
    return myNameHash;
  }

  public @NlsSafe String getPath() {
    StringBuilder builder = new StringBuilder();
    buildPath(this, builder);
    return builder.toString();
  }

  private static void buildPath(Entry e, StringBuilder builder) {
    if (e == null) return;
    Entry parent = e.getParent();
    buildPath(parent, builder);
    String pName = parent == null ? "" : parent.getName();
    if (!builder.isEmpty() && (pName.length() != 1 || pName.charAt(0) != Paths.DELIM)) {
      builder.append(Paths.DELIM);
    }
    builder.append(e.getNameSequence());
  }

  public boolean nameEquals(@NonNls String name) {
    return Paths.equals(getName(), name);
  }

  public boolean pathEquals(@NonNls String path) {
    return Paths.equals(getPath(), path);
  }

  public abstract long getTimestamp();

  public boolean isReadOnly() {
    throw new UnsupportedOperationException(formatPath());
  }

  public void setReadOnly(boolean isReadOnly) {
    throw new UnsupportedOperationException(formatPath());
  }

  public boolean isOutdated(long timestamp) {
    return getTimestamp() != timestamp;
  }

  @ApiStatus.Internal
  public Content getContent() {
    throw new UnsupportedOperationException(formatPath());
  }

  public boolean hasUnavailableContent() {
    return hasUnavailableContent(new ArrayList<>());
  }

  public boolean hasUnavailableContent(List<? super Entry> entriesWithUnavailableContent) {
    return false;
  }

  public Entry getParent() {
    return myParent;
  }

  @ApiStatus.Internal
  protected void setParent(DirectoryEntry parent) {
    myParent = parent;
  }

  public boolean isDirectory() {
    return false;
  }

  public void addChild(Entry child) {
    throw new UnsupportedOperationException(formatAddRemove(child));
  }

  public void addChildren(Collection<? extends Entry> children) {
    throw new UnsupportedOperationException();
  }

  public void removeChild(Entry child) {
    throw new UnsupportedOperationException(formatAddRemove(child));
  }

  private @NonNls String formatAddRemove(Entry child) {
    return "add/remove " + child.formatPath() + " to " + formatPath();
  }

  public List<Entry> getChildren() {
    return Collections.emptyList();
  }

  public Entry findChild(@NonNls String name) {
    int nameHash = calcNameHash(name);
    for (Entry e : getChildren()) {
      if (nameHash == e.getNameHash() && e.nameEquals(name)) return e;
    }
    return null;
  }

  protected static int calcNameHash(@Nullable @NonNls CharSequence name) {
    return name == null ? -1 : StringUtil.stringHashCodeInsensitive(name);
  }

  public boolean hasEntry(@NonNls String path) {
    return findEntry(path) != null;
  }

  public @NotNull Entry getEntry(@NonNls String path) {
    Entry result = findEntry(path);
    if (result == null) {
      throw new RuntimeException(format("entry '%s' not found", path));
    }
    return result;
  }

  public @Nullable Entry findEntry(@NonNls String relativePath) {
    Iterable<String> parts = Paths.split(relativePath);
    Entry result = this;
    for (String each : parts) {
      result = result.findChild(each);
      if (result == null) return null;
    }

    return result;
  }

  public abstract @NotNull Entry copy();

  public void setName(@NonNls String newName) {
    if (myParent != null) myParent.checkDoesNotExist(this, newName);
    myNameId = toNameId(newName);
    myNameHash = calcNameHash(newName);
  }

  @ApiStatus.Internal
  public void setContent(Content newContent, long timestamp) {
    throw new UnsupportedOperationException(formatPath());
  }

  public static List<Difference> getDifferencesBetween(@Nullable Entry left, @Nullable Entry right) {
    return getDifferencesBetween(left, right, false);
  }

  public static @NotNull List<Difference> getDifferencesBetween(@Nullable Entry left,
                                                                @Nullable Entry right,
                                                                boolean isRightContentCurrent) {
    List<Difference> result = new SmartList<>();
    BiConsumer<Entry, Entry> consumer = (leftEntry, rightEntry) -> {
      result.add(new Difference(leftEntry, rightEntry, isRightContentCurrent));
    };

    if (left != null && right != null) {
      left.collectDifferencesWith(right, consumer);
    }
    else if (right != null) {
      right.collectCreatedDifferences(consumer);
    }
    else if (left != null) {
      left.collectDeletedDifferences(consumer);
    }
    return result;
  }

  protected abstract void collectDifferencesWith(@NotNull Entry e, @NotNull BiConsumer<Entry, Entry> consumer);

  protected abstract void collectCreatedDifferences(@NotNull BiConsumer<Entry, Entry> consumer);

  protected abstract void collectDeletedDifferences(@NotNull BiConsumer<Entry, Entry> consumer);

  @Override
  public String toString() {
    return getName();
  }

  private @NonNls String formatPath() {
    String type = isDirectory() ? "dir: " : "file: ";
    return type + getPath();
  }
}
