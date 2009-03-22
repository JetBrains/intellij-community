package com.intellij.refactoring.listeners.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.impl.RefactoringTransactionImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
public class RefactoringListenerManagerImpl extends RefactoringListenerManager {
  private final ArrayList<RefactoringElementListenerProvider> myListenerProviders;
  private final Project myProject;

  public RefactoringListenerManagerImpl(Project project) {
    myProject = project;
    myListenerProviders = new ArrayList<RefactoringElementListenerProvider>();
  }

  public void addListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.add(provider);
  }

  public void removeListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.remove(provider);
  }

  public RefactoringTransaction startTransaction() {
    List<RefactoringElementListenerProvider> providers = new ArrayList<RefactoringElementListenerProvider>(myListenerProviders);
    Collections.addAll(providers, Extensions.getExtensions(RefactoringElementListenerProvider.EP_NAME, myProject));
    return new RefactoringTransactionImpl(providers);
  }
}
