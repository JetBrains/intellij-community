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

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.ConfigurationContext");
  private final Location<PsiElement> myLocation;
  private RunnerAndConfigurationSettings myConfiguration;
  private Ref<RunnerAndConfigurationSettings> myExistingConfiguration;
  private final Module myModule;
  private final RuntimeConfiguration myRuntimeConfiguration;
  private final Component myContextComponent;

  public static DataKey<ConfigurationContext> SHARED_CONTEXT = DataKey.create("SHARED_CONTEXT");
  private List<RuntimeConfigurationProducer> myPreferredProducers;

  public static ConfigurationContext getFromContext(DataContext dataContext) {
    final ConfigurationContext context = new ConfigurationContext(dataContext);
    final DataManager dataManager = DataManager.getInstance();
    ConfigurationContext sharedContext = dataManager.loadFromDataContext(dataContext, SHARED_CONTEXT);
    if (sharedContext == null || !Comparing.equal(sharedContext.getLocation().getPsiElement(), context.getLocation().getPsiElement())) {
      sharedContext = context;
      dataManager.saveInDataContext(dataContext, SHARED_CONTEXT, sharedContext);
    }
    return sharedContext;
  }

  private ConfigurationContext(final DataContext dataContext) {
    myRuntimeConfiguration = RuntimeConfiguration.DATA_KEY.getData(dataContext);
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    myModule = LangDataKeys.MODULE.getData(dataContext);
    @SuppressWarnings({"unchecked"})
    final Location<PsiElement> location = (Location<PsiElement>)Location.DATA_KEY.getData(dataContext);
    if (location != null) {
      myLocation = location;
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

  public RunnerAndConfigurationSettings getConfiguration() {
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
  public RunnerAndConfigurationSettings findExisting() {
    if (myExistingConfiguration != null) return myExistingConfiguration.get();
    myExistingConfiguration = new Ref<RunnerAndConfigurationSettings>();
    if (myLocation == null) {
      return null;
    }

    final List<RuntimeConfigurationProducer> producers = findPreferredProducers();
    if (producers == null) return null;
    if (myRuntimeConfiguration != null) {
      for (RuntimeConfigurationProducer producer : producers) {
        final RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(myLocation, this);
        if (configuration != null && configuration.getConfiguration() == myRuntimeConfiguration) {
          myExistingConfiguration.set(configuration);
        }
      }
    }
    for (RuntimeConfigurationProducer producer : producers) {
      final RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(myLocation, this);
      if (configuration != null) {
        myExistingConfiguration.set(configuration);
      }
    }
    return myExistingConfiguration.get();
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

  public RunManager getRunManager() {
    return RunManager.getInstance(getProject());
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

  @Nullable
  public List<RuntimeConfigurationProducer> findPreferredProducers() {
    if (myPreferredProducers == null) {
      myPreferredProducers = PreferedProducerFind.findPreferredProducers(myLocation, this, true);
    }
    return myPreferredProducers;
  }
}
