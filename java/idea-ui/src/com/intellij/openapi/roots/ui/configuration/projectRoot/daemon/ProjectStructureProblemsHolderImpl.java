package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureProblemsHolderImpl implements ProjectStructureProblemsHolder {
  private List<ProjectStructureProblemDescription> myProblemDescriptions;

  public void registerError(@NotNull String message, String description, @NotNull PlaceInProjectStructure place, @Nullable ConfigurationErrorQuickFix fix) {
    final List<ConfigurationErrorQuickFix> fixes = fix != null ? Collections.singletonList(fix) : Collections.<ConfigurationErrorQuickFix>emptyList();
    registerProblem(new ProjectStructureProblemDescription(message, description, ProjectStructureProblemDescription.Severity.ERROR, place, fixes));
  }

  public void registerWarning(@NotNull String message, String description, @NotNull PlaceInProjectStructure place, @Nullable ConfigurationErrorQuickFix fix) {
    final List<ConfigurationErrorQuickFix> fixes = Collections.singletonList(fix);
    registerProblem(new ProjectStructureProblemDescription(message, description, ProjectStructureProblemDescription.Severity.WARNING, place, fixes));
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
      buf.append("<html><body>");
      if (myProblemDescriptions != null) {
        int problems = 0;
        for (ProjectStructureProblemDescription problemDescription : myProblemDescriptions) {
          buf.append(StringUtil.escapeXml(problemDescription.getMessage())).append("<br>");
          problems++;
          if (problems >= 10 && myProblemDescriptions.size() > 12) {
            buf.append(myProblemDescriptions.size() - problems).append(" more problems...<br>");
            break;
          }
        }
      }
      buf.append("</body></html>");
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
