// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class PreloadedData extends UserDataHolderBase {
  private @Nullable BuildRunner runner;
  private @Nullable ProjectDescriptor projectDescriptor;
  
  private final List<BuildMessage> loadMessages = new ArrayList<>();
  
  private long fsEventOrdinal;
  private boolean hasWorkFlag = true;

  public @Nullable BuildRunner getRunner() {
    return runner;
  }

  public void setRunner(@Nullable BuildRunner runner) {
    this.runner = runner;
  }

  public @Nullable ProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void setProjectDescriptor(@Nullable ProjectDescriptor projectDescriptor) {
    this.projectDescriptor = projectDescriptor;
  }

  public long getFsEventOrdinal() {
    return fsEventOrdinal;
  }

  public void setFsEventOrdinal(long fsEventOrdinal) {
    this.fsEventOrdinal = fsEventOrdinal;
  }

  public List<BuildMessage> getLoadMessages() {
    return loadMessages;
  }
  
  public void addMessage(BuildMessage msg) {
    loadMessages.add(msg);
  }

  public boolean hasWorkToDo() {
    return hasWorkFlag;
  }

  public void setHasHasWorkToDo(boolean hasWorkFlag) {
    this.hasWorkFlag = hasWorkFlag;
  }

  @Override
  public String toString() {
    return "PreloadedData(fsEventOrdinal=" + fsEventOrdinal + ", hasWorkFlag=" + hasWorkFlag + ")";
  }
}
