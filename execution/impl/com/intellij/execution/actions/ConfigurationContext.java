package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.ConfigurationContext");
  private final Location<PsiElement> myLocation;
  private RunnerAndConfigurationSettingsImpl myConfiguration;
  private Module myModule;
  private RuntimeConfiguration myRuntimeConfiguration;
  private Component myContextComponent;

  public ConfigurationContext(final DataContext dataContext) {
    myRuntimeConfiguration = (RuntimeConfiguration)dataContext.getData(DataConstantsEx.RUNTIME_CONFIGURATION);
    myContextComponent = (Component)dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT);
    myModule = (Module)dataContext.getData(DataConstants.MODULE);
    final Object location = dataContext.getData(Location.LOCATION);
    if (location != null) {
      myLocation = (Location<PsiElement>)location;
      return;
    }
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      myLocation = null;
      return;
    }
    final PsiElement element = getSelectedPsiElement(dataContext, project);
    if (element == null) {
      myLocation = null;
      return;
    }
    myLocation = new PsiLocation<PsiElement>(project, element);
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

  public RunnerAndConfigurationSettingsImpl findExisting() {
    final List<ConfigurationType> types = new ArrayList<ConfigurationType>();
    if (myRuntimeConfiguration != null) {
      types.add(myRuntimeConfiguration.getType());
    } else {
      final List<RuntimeConfigurationProducer> producers = PreferedProducerFind.findPreferedProducers(myLocation, this);
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
        if (factoryLocatable.isConfigurationByElement(existingConfiguration.getConfiguration(), getProject(), myLocation.getPsiElement())) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }

  private static PsiElement getSelectedPsiElement(final DataContext dataContext, final Project project) {
    PsiElement element = null;
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor != null){
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      }
    }
    if (element == null) {
      element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    }
    if (element == null) {
      final VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
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
    return myRuntimeConfiguration != null && type.equals(myRuntimeConfiguration.getType()) ? myRuntimeConfiguration : null;
  }
}
