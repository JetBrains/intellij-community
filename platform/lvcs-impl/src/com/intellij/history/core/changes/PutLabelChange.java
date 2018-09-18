// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core.changes;

import com.intellij.history.core.Content;
import com.intellij.history.core.StreamUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PutLabelChange extends Change {
  @NotNull private final String myName;
  @NotNull private final String myProjectId;

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

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
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

  @Override
  public boolean isCreationalFor(String path) {
    return false;
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }

  @Override
  public void accept(ChangeVisitor v) throws ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
