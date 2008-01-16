package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.packageDependencies.ui.DependenciesPanel;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import javax.swing.*;
import java.util.*;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesHandler {
  private final Project myProject;
  private final List<AnalysisScope> myScopes;
  private final AnalysisScope myScopeOfInterest;
  private final Set<PsiFile> myExcluded;

  public BackwardDependenciesHandler(Project project, AnalysisScope scope, final AnalysisScope selectedScope) {
    this(project, Collections.singletonList(scope), selectedScope, new HashSet<PsiFile>());
  }

  public BackwardDependenciesHandler(final Project project, final List<AnalysisScope> scopes, final AnalysisScope scopeOfInterest, Set<PsiFile> excluded) {
    myProject = project;
    myScopes = scopes;
    myScopeOfInterest = scopeOfInterest;
    myExcluded = excluded;
  }

  public void analyze() {
    final List<DependenciesBuilder> builders = new ArrayList<DependenciesBuilder>();
    for (AnalysisScope scope : myScopes) {
      builders.add(new BackwardDependenciesBuilder(myProject, scope, myScopeOfInterest));
    }
    final Runnable process = new Runnable() {
      public void run() {
        for (DependenciesBuilder builder : builders) {
          builder.analyze();
        }
      }
    };
    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            DependenciesPanel panel = new DependenciesPanel(myProject, builders, myExcluded);
            Content content = ContentFactory.SERVICE.getInstance().createContent(panel, AnalysisScopeBundle.message(
              "backward.dependencies.toolwindow.title", builders.get(0).getScope().getDisplayName()), false);
            content.setDisposer(panel);
            panel.setContent(content);
            ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
          }
        });
      }
    };

    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(myProject, AnalysisScopeBundle.message("backward.dependencies.progress.text"),
                                            process, successRunnable, null, new PerformAnalysisInBackgroundOption(myProject));

  }
}
