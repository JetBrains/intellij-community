/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.cmdline;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class PreloadedData {
  @Nullable 
  private BuildRunner runner;
  @Nullable
  private ProjectDescriptor projectDescriptor;
  
  private final List<BuildMessage> loadMessages = new ArrayList<>();
  
  private long fsEventOrdinal;
  private boolean hasWorkFlag = true;

  @Nullable
  public BuildRunner getRunner() {
    return runner;
  }

  public void setRunner(@Nullable BuildRunner runner) {
    this.runner = runner;
  }

  @Nullable
  public ProjectDescriptor getProjectDescriptor() {
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
}
