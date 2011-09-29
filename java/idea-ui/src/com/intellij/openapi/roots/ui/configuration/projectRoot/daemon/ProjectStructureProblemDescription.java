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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureProblemDescription {
  private final String myMessage;
  private final String myDescription;
  private final Severity mySeverity;
  private final PlaceInProjectStructure myPlace;
  private final List<ConfigurationErrorQuickFix> myFixes;

  public ProjectStructureProblemDescription(@NotNull String message, @Nullable String description, @NotNull Severity severity, @NotNull PlaceInProjectStructure place,
                                            @NotNull List<ConfigurationErrorQuickFix> fixes) {
    myMessage = message;
    myDescription = description;
    mySeverity = severity;
    myPlace = place;
    myFixes = fixes;
  }

  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public List<ConfigurationErrorQuickFix> getFixes() {
    return myFixes;
  }

  public Severity getSeverity() {
    return mySeverity;
  }

  public PlaceInProjectStructure getPlace() {
    return myPlace;
  }

  public enum Severity { ERROR, WARNING }
}
