package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;

public abstract class ModuleBuilder  {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.ModuleDescriptor");
  private String myName;
  private String myModuleFilePath;

  protected final String acceptParameter(String param) {
    return param != null && param.length() > 0? param : null;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = acceptParameter(name);
  }

  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  public void setModuleFilePath(String path) {
    myModuleFilePath = acceptParameter(path);
  }

  public String getModuleFileDirectory() {
    if (myModuleFilePath == null) {
      return null;
    }
    final String parent = new File(myModuleFilePath).getParent();
    if (parent == null) {
      return null;
    }
    return parent.replace(File.separatorChar, '/');
  }

  public Module createModule(ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    LOG.assertTrue(myName != null);
    LOG.assertTrue(myModuleFilePath != null);

    final ModuleType moduleType = getModuleType();
    final Module module = moduleModel.newModule(myModuleFilePath, moduleType);
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(modifiableModel);
    modifiableModel.commit();
    module.setSavePathsRelative(true); // default setting
    return module;
  }

  public abstract void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException;

  public abstract ModuleType getModuleType();
}
