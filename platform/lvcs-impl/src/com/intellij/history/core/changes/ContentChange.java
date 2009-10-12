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
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.IdPath;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ContentChange extends StructuralChange<ContentChangeNonAppliedState, ContentChangeAppliedState> {
  public ContentChange(String path, Content newContent, long timestamp) {
    super(path);
    getNonAppliedState().myNewContent = newContent;
    getNonAppliedState().myNewTimestamp = timestamp;
  }

  public ContentChange(Stream s) throws IOException {
    super(s);
    getAppliedState().myOldContent = s.readContent();
    getAppliedState().myOldTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeContent(getAppliedState().myOldContent);
    s.writeLong(getAppliedState().myOldTimestamp);
  }

  @Override
  protected ContentChangeAppliedState createAppliedState() {
    return new ContentChangeAppliedState();
  }

  @Override
  protected ContentChangeNonAppliedState createNonAppliedState() {
    return new ContentChangeNonAppliedState();
  }


  public Content getOldContent() {
    return getAppliedState().myOldContent;
  }

  public long getOldTimestamp() {
    return getAppliedState().myOldTimestamp;
  }

  @Override
  protected IdPath doApplyTo(Entry root, ContentChangeAppliedState newState) {
    Entry e = root.getEntry(getPath());

    newState.myOldContent = e.getContent();
    newState.myOldTimestamp = e.getTimestamp();

    e.changeContent(getNonAppliedState().myNewContent, getNonAppliedState().myNewTimestamp);
    return e.getIdPath();
  }

  @Override
  public void doRevertOn(Entry root) {
    Entry e = root.getEntry(getAffectedIdPath());
    e.changeContent(getAppliedState().myOldContent, getAppliedState().myOldTimestamp);
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.singletonList(getAppliedState().myOldContent);
  }

  @Override
  public boolean isFileContentChange() {
    return true;
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
