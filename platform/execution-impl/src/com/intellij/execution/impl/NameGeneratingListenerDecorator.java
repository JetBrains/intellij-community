// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class NameGeneratingListenerDecorator implements RefactoringElementListener, UndoRefactoringElementListener {
  private final LocatableConfiguration myConfiguration;
  private final RefactoringElementListener myListener;

  public NameGeneratingListenerDecorator(LocatableConfiguration configuration, RefactoringElementListener listener) {
    myConfiguration = configuration;
    myListener = listener;
  }

  @Override
  public void elementMoved(@NotNull PsiElement newElement) {
    boolean hasGeneratedName = myConfiguration.isGeneratedName();
    myListener.elementMoved(newElement);
    if (hasGeneratedName) {
      updateSuggestedName();
    }
  }

  @Override
  public void elementRenamed(@NotNull PsiElement newElement) {
    boolean hasGeneratedName = myConfiguration.isGeneratedName();
    myListener.elementRenamed(newElement);
    if (hasGeneratedName) {
      updateSuggestedName();
    }
  }

  @Override
  public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
    if (myListener instanceof UndoRefactoringElementListener) {
      boolean hasGeneratedName = myConfiguration.isGeneratedName();
      ((UndoRefactoringElementListener) myListener).undoElementMovedOrRenamed(newElement, oldQualifiedName);
      if (hasGeneratedName) {
        updateSuggestedName();
      }
    }
  }

  private void updateSuggestedName() {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myConfiguration.getProject());
    myConfiguration.setName(myConfiguration.suggestedName());
    RunnerAndConfigurationSettingsImpl settings = runManager.getSettings(myConfiguration);
    if (settings != null) {
      runManager.addConfiguration(settings);
    }
  }
}
