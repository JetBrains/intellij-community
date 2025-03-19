// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerComposite;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;

public class RunConfigurationRefactoringElementListenerProvider implements RefactoringElementListenerProvider {
  private static final Logger LOG = Logger.getInstance(RunConfigurationRefactoringElementListenerProvider.class);

  @Override
  public RefactoringElementListener getListener(final PsiElement element) {
    RefactoringElementListenerComposite composite = null;
    for (RunConfiguration configuration : RunManager.getInstance(element.getProject()).getAllConfigurationsList()) {
      if (configuration instanceof RefactoringListenerProvider) { // todo: perhaps better way to handle listeners?
        RefactoringElementListener listener;
        try {
          listener = ((RefactoringListenerProvider)configuration).getRefactoringElementListener(element);
        }
        catch (Exception e) {
          LOG.error(e);
          continue;
        }
        if (listener != null) {
          if (configuration instanceof LocatableConfiguration) {
            listener = new NameGeneratingListenerDecorator((LocatableConfiguration)configuration, listener);
          }
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
