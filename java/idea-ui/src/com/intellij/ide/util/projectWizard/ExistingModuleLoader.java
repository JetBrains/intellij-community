/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.ConversionService;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 7, 2004
 */
public class ExistingModuleLoader extends ModuleBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.ExistingModuleLoader");

  @NotNull
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
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

  public boolean validate(final Project current, final Project dest) {
    if (getName() == null) return false;
    String moduleFilePath = getModuleFilePath();
    if (moduleFilePath == null) return false;
    final File file = new File(moduleFilePath);
    if (file.exists()) {
      try {
        final ConversionResult result = ConversionService.getInstance().convertModule(dest, file);
        if (result.openingIsCanceled()) {
          return false;
        }
        final Document document = JDOMUtil.loadDocument(file);
        final Element root = document.getRootElement();
        final Set<String> usedMacros = PathMacrosCollector.getMacroNames(root);
        final Set<String> definedMacros = PathMacros.getInstance().getAllMacroNames();
        usedMacros.remove("$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
        usedMacros.removeAll(definedMacros);

        if (usedMacros.size() > 0) {
          final boolean ok = ProjectMacrosUtil.showMacrosConfigurationDialog(current, usedMacros);
          if (!ok) {
            return false;
          }
        }
      }
      catch (JDOMException e) {
        Messages.showMessageDialog(e.getMessage(), IdeBundle.message("title.error.reading.file"), Messages.getErrorIcon());
        return false;
      }
      catch (IOException e) {
        Messages.showMessageDialog(e.getMessage(), IdeBundle.message("title.error.reading.file"), Messages.getErrorIcon());
        return false;
      }
    } else {
      Messages.showErrorDialog(current, IdeBundle.message("title.module.file.does.not.exist", moduleFilePath),
                               CommonBundle.message("title.error"));
      return false;
    }
    return true;
  }
}
