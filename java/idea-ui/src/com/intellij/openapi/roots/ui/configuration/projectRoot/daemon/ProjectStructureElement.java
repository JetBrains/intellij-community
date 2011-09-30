package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class ProjectStructureElement {
  protected final StructureConfigurableContext myContext;

  protected ProjectStructureElement(@NotNull StructureConfigurableContext context) {
    myContext = context;
  }

  public abstract String getPresentableName();

  public abstract String getId();

  public abstract void check(ProjectStructureProblemsHolder problemsHolder);

  public abstract List<ProjectStructureElementUsage> getUsagesInElement();

  public abstract boolean highlightIfUnused();

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return getId();
  }
}
