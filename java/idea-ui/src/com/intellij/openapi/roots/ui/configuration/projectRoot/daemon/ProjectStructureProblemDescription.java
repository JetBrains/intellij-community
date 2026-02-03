// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProjectStructureProblemDescription {
  public enum ProblemLevel {PROJECT, GLOBAL}
  private final @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String myMessage;
  private final HtmlChunk myDescription;
  private final PlaceInProjectStructure myPlace;
  private final List<? extends ConfigurationErrorQuickFix> myFixes;
  private final ProjectStructureProblemType myProblemType;
  private final ProblemLevel myProblemLevel;
  private final boolean myCanShowPlace;

  public ProjectStructureProblemDescription(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                            @NotNull HtmlChunk description,
                                            @NotNull PlaceInProjectStructure place,
                                            @NotNull ProjectStructureProblemType problemType,
                                            @NotNull List<? extends ConfigurationErrorQuickFix> fixes) {
    this(message, description, place, problemType, ProblemLevel.PROJECT, fixes, true);
  }

  public ProjectStructureProblemDescription(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                            @NotNull HtmlChunk description,
                                            @NotNull PlaceInProjectStructure place,
                                            @NotNull ProjectStructureProblemType problemType,
                                            @NotNull ProblemLevel level,
                                            @NotNull List<? extends ConfigurationErrorQuickFix> fixes, final boolean canShowPlace) {
    myMessage = message;
    myDescription = description;
    myPlace = place;
    myFixes = fixes;
    myProblemType = problemType;
    myProblemLevel = level;
    myCanShowPlace = canShowPlace;
  }

  public ProblemLevel getProblemLevel() {
    return myProblemLevel;
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getMessage() {
    return myMessage;
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getMessage(final boolean includePlace) {
    if (!includePlace || !myCanShowPlace) return myMessage;

    final @NlsSafe String result = myPlace.getContainingElement().getPresentableText() + ": " + StringUtil.decapitalize(myMessage);
    return result;
  }

  public boolean canShowPlace() {
    return myCanShowPlace;
  }

  public @NotNull HtmlChunk getDescription() {
    return myDescription;
  }

  public List<? extends ConfigurationErrorQuickFix> getFixes() {
    return myFixes;
  }

  public ProjectStructureProblemType.Severity getSeverity() {
    return myProblemType.getSeverity();
  }

  public @NotNull PlaceInProjectStructure getPlace() {
    return myPlace;
  }

  public String getId() {
    final String placePath = myPlace.getPlacePath();
    return myProblemType.getId() + "(" + myPlace.getContainingElement().getId() + (placePath != null ? "," + placePath : "") + ")";
  }
}
