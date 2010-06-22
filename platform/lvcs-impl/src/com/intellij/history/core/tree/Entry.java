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

import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.StreamUtil;
import com.intellij.util.SmartList;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public abstract class Entry {
  protected String myName;
  protected DirectoryEntry myParent;

  public Entry(String name) {
    myName = name;
    //assert name == null || !name.contains("/");
  }

  public Entry(DataInput in) throws IOException {
    myName = StreamUtil.readString(in);
  }

  public void write(DataOutput out) throws IOException {
    StreamUtil.writeString(out, myName);
  }

  public String getName() {
    return myName;
  }

  public String getPath() {
    StringBuilder builder = new StringBuilder();
    buildPath(this, builder);
    return builder.toString();
  }

  private void buildPath(Entry e, StringBuilder builder) {
    if (e == null) return;
    buildPath(e.getParent(), builder);
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) != Paths.DELIM) builder.append(Paths.DELIM);
    builder.append(e.getName());
  }

  public boolean nameEquals(String name) {
    return Paths.equals(myName, name);
  }

  public boolean pathEquals(String path) {
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

  public Content getContent() {
    throw new UnsupportedOperationException(formatPath());
  }

  public boolean hasUnavailableContent() {
    return hasUnavailableContent(new ArrayList<Entry>());
  }

  public boolean hasUnavailableContent(List<Entry> entriesWithUnavailableContent) {
    return false;
  }

  public Entry getParent() {
    return myParent;
  }

  protected void setParent(DirectoryEntry parent) {
    myParent = parent;
  }

  public boolean isDirectory() {
    return false;
  }

  public void addChild(Entry child) {
    throw new UnsupportedOperationException(formatAddRemove(child));
  }

  public void removeChild(Entry child) {
    throw new UnsupportedOperationException(formatAddRemove(child));
  }

  private String formatAddRemove(Entry child) {
    return "add/remove " + child.formatPath() + " to " + formatPath();
  }

  public List<Entry> getChildren() {
    return Collections.emptyList();
  }

  public Entry findChild(String name) {
    for (Entry e : getChildren()) {
      if (e.nameEquals(name)) return e;
    }
    return null;
  }

  public boolean hasEntry(String path) {
    return findEntry(path) != null;
  }

  public Entry getEntry(String path) {
    Entry result = findEntry(path);
    if (result == null) {
      throw new RuntimeException(format("entry '%s' not found", path));
    }
    return result;
  }

  public Entry findEntry(String relativePath) {
    Iterable<String> parts = Paths.split(relativePath);
    Entry result = this;
    for (String each : parts) {
      result = result.findChild(each);
      if (result == null) return null;
    }

    return result;
  }

  public abstract Entry copy();

  public void setName(String newName) {
    if (myParent != null) myParent.checkDoesNotExist(this, newName);
    myName = newName;
  }

  public void setContent(Content newContent, long timestamp) {
    throw new UnsupportedOperationException(formatPath());
  }

  public static List<Difference> getDifferencesBetween(Entry left, Entry right) {
    List<Difference> result = new SmartList<Difference>();

    if (left == null) right.collectCreatedDifferences(result);
    else if (right == null) left.collectDeletedDifferences(result);
    else left.collectDifferencesWith(right, result);
    return result;
  }

  protected abstract void collectDifferencesWith(Entry e, List<Difference> result);

  protected abstract void collectCreatedDifferences(List<Difference> result);

  protected abstract void collectDeletedDifferences(List<Difference> result);

  @Override
  public String toString() {
    return myName;
  }

  private String formatPath() {
    String type = isDirectory() ? "dir: " : "file: ";
    return type + getPath();
  }
}
