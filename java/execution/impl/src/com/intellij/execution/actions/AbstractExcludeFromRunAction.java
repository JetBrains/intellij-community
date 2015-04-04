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

import com.intellij.execution.Location;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Set;


public abstract class AbstractExcludeFromRunAction<T extends ModuleBasedConfiguration<JavaRunConfigurationModule>> extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + AbstractExcludeFromRunAction.class.getName());

  protected abstract Set<String> getPattern(T configuration);
  protected abstract boolean isPatternBasedConfiguration(RunConfiguration configuration);

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    LOG.assertTrue(project != null);
    final T configuration = (T)RunConfiguration.DATA_KEY.getData(dataContext);
    LOG.assertTrue(configuration != null);
    final GlobalSearchScope searchScope = configuration.getConfigurationModule().getSearchScope();
    final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(dataContext);
    LOG.assertTrue(testProxy != null);
    final String qualifiedName = ((PsiClass)testProxy.getLocation(project, searchScope).getPsiElement()).getQualifiedName();
    getPattern(configuration).remove(qualifiedName);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final RunConfiguration configuration = RunConfiguration.DATA_KEY.getData(dataContext);
      if (isPatternBasedConfiguration(configuration)) {
        final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(dataContext);
        if (testProxy != null) {
          final Location location = testProxy.getLocation(project, ((T)configuration).getConfigurationModule().getSearchScope());
          if (location != null) {
            final PsiElement psiElement = location.getPsiElement();
            if (psiElement instanceof PsiClass && getPattern((T)configuration).contains(((PsiClass)psiElement).getQualifiedName())) {
              presentation.setVisible(true);
            }
          }
        }
      }
    }
  }
}
