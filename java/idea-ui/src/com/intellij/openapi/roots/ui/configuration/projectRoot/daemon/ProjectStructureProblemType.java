/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ProjectStructureProblemType {
  public enum Severity { ERROR, WARNING, UNUSED }

  private final String myId;
  private final Severity mySeverity;

  public ProjectStructureProblemType(@NotNull String id, @NotNull Severity severity) {
    myId = id;
    mySeverity = severity;
  }

  public static ProjectStructureProblemType error(@NotNull String id) {
    return new ProjectStructureProblemType(id, Severity.ERROR);
  }

  public static ProjectStructureProblemType warning(@NotNull String id) {
    return new ProjectStructureProblemType(id, Severity.WARNING);
  }

  public static ProjectStructureProblemType unused(@NotNull String id) {
    return new ProjectStructureProblemType(id, Severity.UNUSED);
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public Severity getSeverity() {
    return mySeverity;
  }
}
