/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 7, 2004
 */
public class ExistingModuleLoader extends ModuleBuilder{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.ExistingModuleLoader");

  public Module createModule(ModifiableModuleModel moduleModel) throws InvalidDataException,
                                                                       IOException,
                                                                       ModuleWithNameAlreadyExists,
                                                                       JDOMException,
                                                                       ConfigurationException {
    LOG.assertTrue(getName() != null);

    final String moduleFilePath = getModuleFilePath();

    LOG.assertTrue(moduleFilePath != null);
    LOG.assertTrue(new File(moduleFilePath).exists());

    return moduleModel.loadModule(moduleFilePath);
  }

  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    // empty
  }

  public ModuleType getModuleType() {
    return null; // no matter
  }
}
