/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Set;

public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.ConfigurationContext");
  private final Location<PsiElement> myLocation;
  private RunnerAndConfigurationSettingsImpl myConfiguration;
  private final Module myModule;
  private final RuntimeConfiguration myRuntimeConfiguration;
  private final Component myContextComponent;

  public ConfigurationContext(final DataContext dataContext) {
    myRuntimeConfiguration = (RuntimeConfiguration)dataContext.getData(DataConstantsEx.RUNTIME_CONFIGURATION);
    myContextComponent = (Component)dataContext.getData(DataConstants.CONTEXT_COMPONENT);
    myModule = LangDataKeys.MODULE.getData(dataContext);
    final Object location = dataContext.getData(Location.LOCATION);
    if (location != null) {
      myLocation = (Location<PsiElement>)location;
      return;
    }
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      myLocation = null;
      return;
    }
    final PsiElement element = getSelectedPsiElement(dataContext, project);
    if (element == null) {
      myLocation = null;
      return;
    }
    myLocation = new PsiLocation<PsiElement>(project, myModule, element);
  }

  public RunnerAndConfigurationSettingsImpl getConfiguration() {
    if (myConfiguration == null) createConfiguration();
    return myConfiguration;
  }

  private void createConfiguration() {
    LOG.assertTrue(myConfiguration == null);
    final Location location = getLocation();
    myConfiguration = location != null ?
        PreferedProducerFind.createConfiguration(location, this) :
        null;
  }

  @Nullable
  public RunnerAndConfigurationSettings getConfiguration(final RuntimeConfigurationProducer producer) {
    myConfiguration = producer.getConfiguration();
    return myConfiguration;
  }

  Location getLocation() {
    return myLocation;
  }

  @Nullable
  public RunnerAndConfigurationSettingsImpl findExisting() {
    if (myLocation == null) {
      return null;
    }

    final Set<ConfigurationType> types = new HashSet<ConfigurationType>();
    if (myRuntimeConfiguration != null) {
      types.add(myRuntimeConfiguration.getType());
    }
    else {
      final List<RuntimeConfigurationProducer> producers = PreferedProducerFind.findPreferredProducers(myLocation, this, true);
      if (producers == null) return null;
      for (RuntimeConfigurationProducer producer : producers) {
        types.add(producer.createProducer(myLocation, this).getConfigurationType());
      }
    }
    for (ConfigurationType type : types) {
      if (!(type instanceof LocatableConfigurationType)) continue;
      final LocatableConfigurationType factoryLocatable = (LocatableConfigurationType)type;
      final RunnerAndConfigurationSettingsImpl[] configurations = getRunManager().getConfigurationSettings(type);
      for (final RunnerAndConfigurationSettingsImpl existingConfiguration : configurations) {
        if (factoryLocatable.isConfigurationByLocation(existingConfiguration.getConfiguration(), myLocation)) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement getSelectedPsiElement(final DataContext dataContext, final Project project) {
    PsiElement element = null;
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null){
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      }
    }
    if (element == null) {
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    if (element == null) {
      final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
      if (file != null) {
        element = PsiManager.getInstance(project).findFile(file);
      }
    }
    return element;
  }

  public RunManagerEx getRunManager() {
    return RunManagerEx.getInstanceEx(getProject());
  }

  public Project getProject() { return myLocation.getProject(); }

  public Module getModule() {
    return myModule;
  }

  public DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(myContextComponent);
  }

  @Nullable
  public RuntimeConfiguration getOriginalConfiguration(final ConfigurationType type) {
    return myRuntimeConfiguration != null && Comparing.strEqual(type.getId(), myRuntimeConfiguration.getType().getId()) ? myRuntimeConfiguration : null;
  }
}
