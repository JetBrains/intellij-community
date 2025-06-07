// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public abstract class AbstractAddToTestsPatternAction<T extends JavaTestConfigurationBase> extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected abstract @NotNull AbstractPatternBasedConfigurationProducer<T> getPatternBasedProducer();

  protected abstract @NotNull ConfigurationType getConfigurationType();

  protected abstract boolean isPatternBasedConfiguration(T configuration);

  protected abstract Set<String> getPatterns(T configuration);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiElement[] psiElements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements == null) return;
    final LinkedHashSet<PsiElement> classes = new LinkedHashSet<>();
    PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>(classes);
    getPatternBasedProducer().collectTestMembers(psiElements, true, true, processor);

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final List<T> patternConfigurations = collectPatternConfigurations(classes, project);
    if (patternConfigurations.size() == 1) {
      final T configuration = patternConfigurations.get(0);
      for (PsiElement aClass : classes) {
        String qName = getPatternBasedProducer().getQName(aClass);
        if (qName != null) {
          getPatterns(configuration).add(qName);
        }
      }
    } else {
      JBPopupFactory.getInstance().createListPopup(
        new BaseListPopupStep<>(JavaCompilerBundle.message("popup.title.choose.suite.to.add"), patternConfigurations) {
          @Override
          public PopupStep<?> onChosen(T configuration, boolean finalChoice) {
            for (PsiElement aClass : classes) {
              String qName = getPatternBasedProducer().getQName(aClass);
              if (qName != null) {
                getPatterns(configuration).add(qName);
              }
            }
            return FINAL_CHOICE;
          }

          @Override
          public Icon getIconFor(T configuration) {
            return configuration.getIcon();
          }

          @Override
          public @NotNull String getTextFor(T value) {
            return value.getName();
          }
        }).showInBestPositionFor(dataContext);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    final DataContext dataContext = e.getDataContext();
    final PsiElement[] psiElements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements != null) {
      PsiElementProcessor.CollectElementsWithLimit<PsiElement> processor = new PsiElementProcessor.CollectElementsWithLimit<>(2);
      getPatternBasedProducer().collectTestMembers(psiElements, false, false, processor);
      Collection<PsiElement> collection = processor.getCollection();
      if (collection.isEmpty()) return;
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        final List<T> foundConfigurations = collectPatternConfigurations(collection, project);
        if (!foundConfigurations.isEmpty()) {
          presentation.setEnabledAndVisible(true);
          if (foundConfigurations.size() == 1) {
            presentation.setText(ExecutionBundle.message("add.to.temp.suite.0", foundConfigurations.get(0).getName()));
          }
        }
      }
    }
  }

  private List<T> collectPatternConfigurations(Collection<PsiElement> foundClasses, Project project) {
    final List<RunConfiguration> configurations = RunManager.getInstance(project).getConfigurationsList(getConfigurationType());
    final List<T> foundConfigurations = new ArrayList<>();
    for (RunConfiguration configuration : configurations) {
      if (isPatternBasedConfiguration((T)configuration)) {
        if (foundClasses.size() > 1 ||
            foundClasses.size() == 1 && !getPatterns((T)configuration).contains(getPatternBasedProducer().getQName(ContainerUtil.getFirstItem(foundClasses)))) {
          foundConfigurations.add((T)configuration);
        }
      }
    }
    return foundConfigurations;
  }
}
