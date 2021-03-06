package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.ide.JavaUiBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ProjectStructureProblemsHolderImpl implements ProjectStructureProblemsHolder {
  private List<ProjectStructureProblemDescription> myProblemDescriptions;

  @Override
  public void registerProblem(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                              @Nullable @NlsContexts.DetailedDescription String description,
                              @NotNull ProjectStructureProblemType problemType,
                              @NotNull PlaceInProjectStructure place,
                              @Nullable ConfigurationErrorQuickFix fix) {
    final List<ConfigurationErrorQuickFix> fixes = fix != null ? Collections.singletonList(fix) : Collections.emptyList();
    registerProblem(new ProjectStructureProblemDescription(message, description, place, problemType, fixes));
  }

  @Override
  public void registerProblem(final @NotNull ProjectStructureProblemDescription description) {
    if (myProblemDescriptions == null) {
      myProblemDescriptions = new SmartList<>();
    }
    myProblemDescriptions.add(description);
  }

  public @Nls String composeTooltipMessage() {
    final HtmlBuilder buf = new HtmlBuilder();
    if (myProblemDescriptions != null) {
      int problems = 0;
      for (ProjectStructureProblemDescription problemDescription : myProblemDescriptions) {
        buf.appendRaw(XmlStringUtil.convertToHtmlContent(problemDescription.getMessage(false))).br();
        problems++;
        if (problems >= 10 && myProblemDescriptions.size() > 12) {
          buf.append(JavaUiBundle.message("x.more.problems", myProblemDescriptions.size() - problems)).br();
          break;
        }
      }
    }
    return buf.wrapWithHtmlBody().toString();
  }

  public boolean containsProblems() {
    return myProblemDescriptions != null && !myProblemDescriptions.isEmpty();
  }

  public boolean containsProblems(final ProjectStructureProblemType.Severity severity) {
    if (myProblemDescriptions != null) {
      for (ProjectStructureProblemDescription description : myProblemDescriptions) {
        if (description.getSeverity() == severity) {
          return true;
        }
      }
    }
    return false;
  }

  public void removeProblem(@NotNull ProjectStructureProblemDescription description) {
    if (myProblemDescriptions != null) {
      myProblemDescriptions.remove(description);
    }
  }

  @Nullable
  public List<ProjectStructureProblemDescription> getProblemDescriptions() {
    return myProblemDescriptions;
  }
}
