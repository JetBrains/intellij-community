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

public class PutLabelChange extends Change {
  private final String myName;
  private final long myTimestamp;

  public PutLabelChange(String name, long timestamp) {
    myName = name;
    myTimestamp = timestamp;
  }

  public PutLabelChange(Stream s) throws IOException {
    myName = s.readString();
    myTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myName);
    s.writeLong(myTimestamp);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public void applyTo(Entry r) {
  }

  @Override
  public void doRevertOn(Entry root) {
  }

  @Override
  public boolean affects(IdPath... pp) {
    return true;
  }

  @Override
  public boolean affectsOnlyInside(Entry e) {
    throw new UnsupportedOperationException();
  }

  public boolean isCreationalFor(Entry e) {
    return false;
  }

  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }

  @Override
  public boolean isLabel() {
    return true;
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
