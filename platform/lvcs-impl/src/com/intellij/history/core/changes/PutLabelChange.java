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
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PutLabelChange extends Change {
  private final String myName;
  private final String myProjectId;

  public PutLabelChange(long id, @NotNull String name, @NotNull String projectId) {
    super(id);
    myName = name;
    myProjectId = projectId;
  }

  public PutLabelChange(DataInput in) throws IOException {
    super(in);
    myName = StreamUtil.readString(in);
    myProjectId = StreamUtil.readString(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    StreamUtil.writeString(out, myName);
    StreamUtil.writeString(out, myProjectId);
  }

  public String getName() {
    return myName;
  }

  public String getProjectId() {
    return myProjectId;
  }

  @Override
  public boolean affectsPath(String paths) {
    return false;
  }

  @Override
  public boolean affectsProject(String projectId) {
    return myProjectId.equals(projectId);
  }

  @Override
  public boolean affectsMatching(Pattern pattern) {
    return false;
  }

  public boolean isCreationalFor(String path) {
    return false;
  }

  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }

  @Override
  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
