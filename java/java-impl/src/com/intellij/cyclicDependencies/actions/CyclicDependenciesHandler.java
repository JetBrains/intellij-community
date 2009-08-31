package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.cyclicDependencies.ui.CyclicDependenciesPanel;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyValidationManagerImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

import javax.swing.*;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesHandler {
  private final Project myProject;
  private final AnalysisScope myScope;

  public CyclicDependenciesHandler(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject, myScope);
    final Runnable process = new Runnable() {
      public void run() {
        builder.analyze();
      }
    };
    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            CyclicDependenciesPanel panel = new CyclicDependenciesPanel(myProject, builder);
            Content content = PeerFactory.getInstance().getContentFactory().createContent(panel, AnalysisScopeBundle.message(
              "action.analyzing.cyclic.dependencies.in.scope", builder.getScope().getDisplayName()), false);
            content.setDisposer(panel);
            panel.setContent(content);
            ((DependencyValidationManagerImpl)DependencyValidationManager.getInstance(myProject)).addContent(content);
          }
        });
      }
    };
    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(myProject, AnalysisScopeBundle.message("package.dependencies.progress.title"),
                                            process, successRunnable, null, new PerformAnalysisInBackgroundOption(myProject));
  }
}
