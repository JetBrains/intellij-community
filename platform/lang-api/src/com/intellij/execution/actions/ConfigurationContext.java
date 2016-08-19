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
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Context for creating run configurations from a location in the source code.
 *
 * @see RunConfigurationProducer
 */
public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.ConfigurationContext");
  private final Location<PsiElement> myLocation;
  private RunnerAndConfigurationSettings myConfiguration;
  private boolean myInitialized = false;
  private Ref<RunnerAndConfigurationSettings> myExistingConfiguration;
  private final Module myModule;
  private final RunConfiguration myRuntimeConfiguration;
  private final Component myContextComponent;

  public static Key<ConfigurationContext> SHARED_CONTEXT = Key.create("SHARED_CONTEXT");
  private List<RuntimeConfigurationProducer> myPreferredProducers;
  private List<ConfigurationFromContext> myConfigurationsFromContext;

  @NotNull
  public static ConfigurationContext getFromContext(DataContext dataContext) {
    final ConfigurationContext context = new ConfigurationContext(dataContext);
    final DataManager dataManager = DataManager.getInstance();
    ConfigurationContext sharedContext = dataManager.loadFromDataContext(dataContext, SHARED_CONTEXT);
    if (sharedContext == null ||
        sharedContext.getLocation() == null ||
        context.getLocation() == null ||
        !Comparing.equal(sharedContext.getLocation().getPsiElement(), context.getLocation().getPsiElement())) {
      sharedContext = context;
      dataManager.saveInDataContext(dataContext, SHARED_CONTEXT, sharedContext);
    }
    return sharedContext;
  }

  private ConfigurationContext(final DataContext dataContext) {
    myRuntimeConfiguration = RunConfiguration.DATA_KEY.getData(dataContext);
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    myModule = LangDataKeys.MODULE.getData(dataContext);
    @SuppressWarnings({"unchecked"})
    final Location<PsiElement> location = (Location<PsiElement>)Location.DATA_KEY.getData(dataContext);
    if (location != null) {
      myLocation = location;
      return;
    }
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      myLocation = null;
      return;
    }
    final PsiElement element = getSelectedPsiElement(dataContext, project);
    if (element == null) {
      myLocation = null;
      return;
    }
    myLocation = new PsiLocation<>(project, myModule, element);
  }

  public ConfigurationContext(PsiElement element) {
    myModule = ModuleUtilCore.findModuleForPsiElement(element);
    myLocation = new PsiLocation<>(element.getProject(), myModule, element);
    myRuntimeConfiguration = null;
    myContextComponent = null;
  }

  /**
   * Returns the configuration created from this context.
   *
   * @return the configuration, or null if none of the producers were able to create a configuration from this context.
   */
  @Nullable
  public synchronized RunnerAndConfigurationSettings getConfiguration() {
    if (myConfiguration == null && !myInitialized) {
      createConfiguration();
    }
    return myConfiguration;
  }

  private void createConfiguration() {
    LOG.assertTrue(myConfiguration == null);
    final Location location = getLocation();
    myConfiguration = location != null && !DumbService.isDumb(location.getProject()) ?
        PreferredProducerFind.createConfiguration(location, this) :
        null;
    myInitialized = true;
  }

  public synchronized void setConfiguration(@NotNull RunnerAndConfigurationSettings configuration) {
    myConfiguration = configuration;
    myInitialized = true;
  }

  @Deprecated
  @Nullable
  public RunnerAndConfigurationSettings updateConfiguration(final RuntimeConfigurationProducer producer) {
    myConfiguration = producer.getConfiguration();
    return myConfiguration;
  }

  /**
   * Returns the source code location for this context.
   *
   * @return the source code location, or null if no source code fragment is currently selected.
   */
  @Nullable
  public Location getLocation() {
    return myLocation;
  }

  /**
   * Returns the PSI element at caret for this context.
   *
   * @return the PSI element, or null if no source code fragment is currently selected.
   */
  @Nullable
  public PsiElement getPsiLocation() {
    return myLocation != null ? myLocation.getPsiElement() : null;
  }

  /**
   * Finds an existing run configuration matching the context.
   *
   * @return an existing configuration, or null if none was found.
   */
  @Nullable
  public RunnerAndConfigurationSettings findExisting() {
    if (myExistingConfiguration != null) return myExistingConfiguration.get();
    myExistingConfiguration = new Ref<>();
    if (myLocation == null) {
      return null;
    }

    final PsiElement psiElement = myLocation.getPsiElement();
    if (!psiElement.isValid()) {
      return null;
    }

    final List<RuntimeConfigurationProducer> producers = findPreferredProducers();
    if (myRuntimeConfiguration != null) {
      if (producers != null) {
        for (RuntimeConfigurationProducer producer : producers) {
          final RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(myLocation, this);
          if (configuration != null && configuration.getConfiguration() == myRuntimeConfiguration) {
            myExistingConfiguration.set(configuration);
          }
        }
      }
      for (RunConfigurationProducer producer : RunConfigurationProducer.getProducers(getProject())) {
        RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(this);
        if (configuration != null && configuration.getConfiguration() == myRuntimeConfiguration) {
          myExistingConfiguration.set(configuration);
        }
      }
    }
    if (producers != null) {
      for (RuntimeConfigurationProducer producer : producers) {
        final RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(myLocation, this);
        if (configuration != null) {
          myExistingConfiguration.set(configuration);
        }
      }
    }
    for (RunConfigurationProducer producer : RunConfigurationProducer.getProducers(getProject())) {
      RunnerAndConfigurationSettings configuration = producer.findExistingConfiguration(this);
      if (configuration != null) {
        myExistingConfiguration.set(configuration);
      }
    }
    return myExistingConfiguration.get();
  }

  @Nullable
  private static PsiElement getSelectedPsiElement(final DataContext dataContext, final Project project) {
    PsiElement element = null;
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null){
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        final int offset = editor.getCaretModel().getOffset();
        element = psiFile.findElementAt(offset);
        if (element == null && offset > 0 && offset == psiFile.getTextLength()) {
          element = psiFile.findElementAt(offset-1);
        }
      }
    }
    if (element == null) {
      final PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      element = elements != null && elements.length > 0 ? elements[0] : null;
    }
    if (element == null) {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null && files.length > 0) {
        element = PsiManager.getInstance(project).findFile(files[0]);
      }
    }
    return element;
  }

  public RunManager getRunManager() {
    return RunManager.getInstance(getProject());
  }

  public Project getProject() {
    return myLocation.getProject();
  }

  public Module getModule() {
    return myModule;
  }

  public DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(myContextComponent);
  }

  /**
   * Returns original {@link RunConfiguration} from this context.
   * For example, it could be some test framework runtime configuration that had been launched
   * and that had brought a result test tree on which a right-click action was performed.
   *
   * @param type {@link ConfigurationType} instance to filter original runtime configuration by its type
   * @return {@link RunConfiguration} instance, it could be null
   */
  @Nullable
  public RunConfiguration getOriginalConfiguration(@Nullable ConfigurationType type) {
    if (type == null) {
      return myRuntimeConfiguration;
    }
    if (myRuntimeConfiguration != null
        && ConfigurationTypeUtil.equals(myRuntimeConfiguration.getType(), type)) {
      return myRuntimeConfiguration;
    }
    return null;
  }

  @Deprecated
  @Nullable
  public List<RuntimeConfigurationProducer> findPreferredProducers() {
    if (myPreferredProducers == null) {
      myPreferredProducers = PreferredProducerFind.findPreferredProducers(myLocation, this, true);
    }
    return myPreferredProducers;
  }

  @Nullable
  public List<ConfigurationFromContext> getConfigurationsFromContext() {
    if (myConfigurationsFromContext == null) {
      myConfigurationsFromContext = PreferredProducerFind.getConfigurationsFromContext(myLocation, this, true);
    }
    return myConfigurationsFromContext;
  }
}
