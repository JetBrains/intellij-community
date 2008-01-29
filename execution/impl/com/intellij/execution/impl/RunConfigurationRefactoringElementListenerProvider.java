package com.intellij.execution.impl;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
*/
public class RunConfigurationRefactoringElementListenerProvider implements RefactoringElementListenerProvider, ProjectComponent {
  private Project myProject;

  public RunConfigurationRefactoringElementListenerProvider(@NotNull final Project project) {
    myProject = project;
  }

  @NotNull
  public String getComponentName() {
    return "RunConfigurationRefactoringElementListenerProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myProject = null;
  }

  public void projectOpened() {
    RefactoringListenerManager.getInstance(myProject).addListenerProvider(this);
  }

  public void projectClosed() {
    RefactoringListenerManager.getInstance(myProject).removeListenerProvider(this);
  }

  public RefactoringElementListener getListener(final PsiElement element) {
    RefactoringElementListenerComposite composite = null;
    final RunConfiguration[] configurations = RunManager.getInstance(element.getProject()).getAllConfigurations();

    for (RunConfiguration configuration : configurations) {
      if (configuration instanceof RefactoringListenerProvider) { // todo: perhaps better way to handle listeners?
        final RefactoringElementListener listener = ((RefactoringListenerProvider)configuration).getRefactoringElementListener(element);
        if (listener != null) {
          if (composite == null) {
            composite = new RefactoringElementListenerComposite();
          }
          composite.addListener(listener);
        }
      }
    }
    return composite;
  }
}
