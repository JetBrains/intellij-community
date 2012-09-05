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
import com.intellij.history.core.StoredContent;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ContentChange extends StructuralChange {
  private final Content myOldContent;
  private final long myOldTimestamp;

  public ContentChange(long id, String path, Content oldContent, long oldTimestamp) {
    super(id, path);
    myOldContent = oldContent;
    myOldTimestamp = oldTimestamp;
  }

  public ContentChange(DataInput in) throws IOException {
    super(in);
    myOldContent = new StoredContent(in);
    myOldTimestamp = in.readLong();
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    myOldContent.write(out);
    out.writeLong(myOldTimestamp);
  }

  public Content getOldContent() {
    return myOldContent;
  }

  public long getOldTimestamp() {
    return myOldTimestamp;
  }

  @Override
  public void revertOn(RootEntry root, boolean warnOnFileNotFound) {
    Entry e = root.findEntry(myPath);
    if (e == null) {
      cannotRevert(myPath, warnOnFileNotFound);
      return;
    }
    e.setContent(myOldContent, myOldTimestamp);
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.singletonList(myOldContent);
  }

  @Override
  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
