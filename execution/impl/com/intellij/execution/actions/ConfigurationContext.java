package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

public class ConfigurationContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.ConfigurationContext");
  private final Location<PsiElement> myLocation;
  private final DataContext myDataContext;
  private RunnerAndConfigurationSettingsImpl myConfiguration;

  public ConfigurationContext(final DataContext dataContext) {
    myDataContext = dataContext;
    final Object location = myDataContext.getData(Location.LOCATION);
    if (location != null) {
      myLocation = (Location<PsiElement>)location;
      return;
    }
    final Project project = (Project)myDataContext.getData(DataConstants.PROJECT);
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
    final RuntimeConfiguration configuration = (RuntimeConfiguration)myDataContext.getData(DataConstantsEx.RUNTIME_CONFIGURATION);
    final ConfigurationType type;
    if (configuration != null) {
      type = configuration.getType();
    } else {
      type = getConfiguration().getType();
    }
    if (!(type instanceof LocatableConfigurationType)) return null;
    final LocatableConfigurationType factoryLocatable = (LocatableConfigurationType)type;
    final RunnerAndConfigurationSettingsImpl[] configurations = getRunManager().getConfigurationSettings(type);
    for (final RunnerAndConfigurationSettingsImpl existingConfiguration : configurations) {
      if (factoryLocatable.isConfigurationByElement(existingConfiguration.getConfiguration(), getProject(), myLocation.getPsiElement())) {
        return existingConfiguration;
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

  public DataContext getDataContext() {
    return myDataContext;
  }

  @Nullable
  public RuntimeConfiguration getOriginalConfiguration(final ConfigurationType type) {
    final RuntimeConfiguration config = (RuntimeConfiguration)myDataContext.getData(DataConstantsEx.RUNTIME_CONFIGURATION);
    return config != null && type.equals(config.getType()) ? config : null;
  }
}
