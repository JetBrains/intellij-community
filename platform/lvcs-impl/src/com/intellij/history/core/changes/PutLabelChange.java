// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core.changes;

import com.intellij.history.core.Content;
import com.intellij.history.core.DataStreamUtil;
import com.intellij.history.core.HistoryPathFilter;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PutLabelChange extends Change {
  private final @NotNull @NlsContexts.Label String myName;
  private final @NotNull String myProjectId;

  public PutLabelChange(long id, @NotNull @NlsContexts.Label String name, @NotNull String projectId) {
    super(id);
    myName = name;
    myProjectId = projectId;
  }

  public PutLabelChange(DataInput in) throws IOException {
    super(in);
    myName = DataStreamUtil.readString(in); //NON-NLS
    myProjectId = DataStreamUtil.readString(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    super.write(out);
    DataStreamUtil.writeString(out, myName);
    DataStreamUtil.writeString(out, myProjectId);
  }

  public @NlsContexts.Label @NotNull String getName() {
    return myName;
  }

  public @NotNull String getProjectId() {
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
  public boolean affectsMatching(@NotNull Pattern pattern) {
    return false;
  }

  @Override
  public boolean affectsMatching(@NotNull HistoryPathFilter historyPathFilter) {
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
