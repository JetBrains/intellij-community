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
  public enum ProblemLevel {PROJECT, GLOBAL}
  private final String myMessage;
  private final String myDescription;
  private final PlaceInProjectStructure myPlace;
  private final List<? extends ConfigurationErrorQuickFix> myFixes;
  private final ProjectStructureProblemType myProblemType;
  private final ProblemLevel myProblemLevel;

  public ProjectStructureProblemDescription(@NotNull String message,
                                            @Nullable String description,
                                            @NotNull PlaceInProjectStructure place,
                                            @NotNull ProjectStructureProblemType problemType,
                                            @NotNull List<? extends ConfigurationErrorQuickFix> fixes) {
    this(message, description, place, problemType, ProblemLevel.PROJECT, fixes);
  }

  public ProjectStructureProblemDescription(@NotNull String message,
                                            @Nullable String description,
                                            @NotNull PlaceInProjectStructure place,
                                            @NotNull ProjectStructureProblemType problemType,
                                            @NotNull ProblemLevel level, @NotNull List<? extends ConfigurationErrorQuickFix> fixes) {
    myMessage = message;
    myDescription = description;
    myPlace = place;
    myFixes = fixes;
    myProblemType = problemType;
    myProblemLevel = level;
  }

  public ProblemLevel getProblemLevel() {
    return myProblemLevel;
  }

  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public List<? extends ConfigurationErrorQuickFix> getFixes() {
    return myFixes;
  }

  public ProjectStructureProblemType.Severity getSeverity() {
    return myProblemType.getSeverity();
  }

  public PlaceInProjectStructure getPlace() {
    return myPlace;
  }

  public String getId() {
    final String placePath = myPlace.getPlacePath();
    return myProblemType.getId() + "(" + myPlace.getContainingElement().getId() + (placePath != null ? "," + placePath : "") + ")";
  }
}
