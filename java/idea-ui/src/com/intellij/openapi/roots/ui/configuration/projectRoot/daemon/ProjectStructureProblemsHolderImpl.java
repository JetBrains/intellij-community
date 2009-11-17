package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.util.SmartList;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureProblemsHolderImpl implements ProjectStructureProblemsHolder {
  private List<ProjectStructureProblemDescription> myProblemDescriptions;

  public void registerError(@NotNull String message) {
    registerProblem(new ProjectStructureProblemDescription(message, ProjectStructureProblemDescription.Severity.ERROR));
  }

  public void registerWarning(@NotNull String message) {
    registerProblem(new ProjectStructureProblemDescription(message, ProjectStructureProblemDescription.Severity.WARNING));
  }

  public void registerProblem(final @NotNull ProjectStructureProblemDescription description) {
    if (myProblemDescriptions == null) {
      myProblemDescriptions = new SmartList<ProjectStructureProblemDescription>();
    }
    myProblemDescriptions.add(description);
  }

  @Nullable
  public ProjectStructureProblemDescription.Severity getSeverity() {
    if (myProblemDescriptions == null || myProblemDescriptions.isEmpty()) {
      return null;
    }
    for (ProjectStructureProblemDescription description : myProblemDescriptions) {
      if (description.getSeverity() == ProjectStructureProblemDescription.Severity.ERROR) {
        return ProjectStructureProblemDescription.Severity.ERROR;
      }
    }
    return ProjectStructureProblemDescription.Severity.WARNING;
  }

  public String composeTooltipMessage() {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      if (myProblemDescriptions != null) {
        for (ProjectStructureProblemDescription problemDescription : myProblemDescriptions) {
          buf.append(problemDescription.getMessage()).append("\n");
        }
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  @Nullable
  public List<ProjectStructureProblemDescription> getProblemDescriptions() {
    return myProblemDescriptions;
  }
}
