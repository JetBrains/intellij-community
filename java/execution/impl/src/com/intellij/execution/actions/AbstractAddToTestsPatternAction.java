/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public abstract class AbstractAddToTestsPatternAction<T extends ModuleBasedConfiguration> extends AnAction {
  @NotNull protected abstract AbstractPatternBasedConfigurationProducer<T> getPatternBasedProducer();

  @NotNull protected abstract ConfigurationType getConfigurationType();

  protected abstract boolean isPatternBasedConfiguration(T configuration);

  protected abstract Set<String> getPatterns(T configuration);

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    final LinkedHashSet<PsiElement> classes = new LinkedHashSet<>();
    PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>(classes);
    getPatternBasedProducer().collectTestMembers(psiElements, true, true, processor);

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final List<T> patternConfigurations = collectPatternConfigurations(classes, project);
    if (patternConfigurations.size() == 1) {
      final T configuration = patternConfigurations.get(0);
      for (PsiElement aClass : classes) {
        getPatterns(configuration).add(AbstractPatternBasedConfigurationProducer.getQName(aClass));
      }
    } else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<T>("Choose suite to add", patternConfigurations) {
        @Override
        public PopupStep onChosen(T configuration, boolean finalChoice) {
          for (PsiElement aClass : classes) {
            getPatterns(configuration).add(AbstractPatternBasedConfigurationProducer.getQName(aClass));
          }
          return FINAL_CHOICE;
        }

        @Override
        public Icon getIconFor(T configuration) {
          return configuration.getIcon();
        }

        @NotNull
        @Override
        public String getTextFor(T value) {
          return value.getName();
        }
      }).showInBestPositionFor(dataContext);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    final DataContext dataContext = e.getDataContext();
    final PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements != null) {
      PsiElementProcessor.CollectElementsWithLimit<PsiElement> processor = new PsiElementProcessor.CollectElementsWithLimit<>(2);
      getPatternBasedProducer().collectTestMembers(psiElements, false, false, processor);
      Collection<PsiElement> collection = processor.getCollection();
      if (collection.isEmpty()) return;
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        final List<T> foundConfigurations = collectPatternConfigurations(collection, project);
        if (!foundConfigurations.isEmpty()) {
          presentation.setVisible(true);
          if (foundConfigurations.size() == 1) {
            presentation.setText("Add to temp suite: " + foundConfigurations.get(0).getName());
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
            !getPatterns((T)configuration).contains(AbstractPatternBasedConfigurationProducer.getQName(foundClasses.iterator().next()))) {
          foundConfigurations.add((T)configuration);
        }
      }
    }
    return foundConfigurations;
  }
}
