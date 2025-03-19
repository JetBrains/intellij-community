// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ProjectStructureElement {
  protected final StructureConfigurableContext myContext;

  protected ProjectStructureElement(@NotNull StructureConfigurableContext context) {
    myContext = context;
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableText() {
    return getTypeName() + " '" + getPresentableName() + "'";
  }

  public abstract @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableName();

  public @Nullable @Nls String getDescription() {
    return null;
  }

  public abstract @Nls(capitalization = Nls.Capitalization.Sentence) String getTypeName();

  public abstract String getId();

  public abstract void check(ProjectStructureProblemsHolder problemsHolder);

  public abstract List<ProjectStructureElementUsage> getUsagesInElement();


  public boolean shouldShowWarningIfUnused() {
    return false;
  }

  public @Nullable ProjectStructureProblemDescription createUnusedElementWarning() {
    return null;
  }


  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return getId();
  }
}
