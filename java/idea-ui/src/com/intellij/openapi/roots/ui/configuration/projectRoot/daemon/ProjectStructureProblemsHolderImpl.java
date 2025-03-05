// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProjectStructureProblemsHolderImpl implements ProjectStructureProblemsHolder {
  private List<ProjectStructureProblemDescription> myProblemDescriptions;

  @Override
  public void registerProblem(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                              @Nullable @NlsContexts.DetailedDescription String description,
                              @NotNull ProjectStructureProblemType problemType,
                              @NotNull PlaceInProjectStructure place,
                              @Nullable ConfigurationErrorQuickFix fix) {
    final List<ConfigurationErrorQuickFix> fixes = ContainerUtil.createMaybeSingletonList(fix);
    registerProblem(new ProjectStructureProblemDescription(message,
                                                           description != null ? HtmlChunk.raw(description) : HtmlChunk.empty(),
                                                           place,
                                                           problemType,
                                                           ProjectStructureProblemDescription.ProblemLevel.PROJECT,
                                                           fixes,
                                                           true));
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

  public @Nullable List<ProjectStructureProblemDescription> getProblemDescriptions() {
    return myProblemDescriptions;
  }
}
