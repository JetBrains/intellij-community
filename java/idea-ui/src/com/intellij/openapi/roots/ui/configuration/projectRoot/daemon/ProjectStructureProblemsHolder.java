package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.util.SmartList;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureProblemsHolder {
  private List<ProjectStructureProblemDescription> myProblemDescriptions;

  public void addError(String message) {
    addProblem(message, ProjectStructureProblemDescription.Severity.ERROR);
  }

  public void addWarning(String message) {
    addProblem(message, ProjectStructureProblemDescription.Severity.WARNING);
  }

  public void addProblem(String description, ProjectStructureProblemDescription.Severity severity) {
    if (myProblemDescriptions == null) {
      myProblemDescriptions = new SmartList<ProjectStructureProblemDescription>();
    }
    myProblemDescriptions.add(new ProjectStructureProblemDescription(description, severity));
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
}
