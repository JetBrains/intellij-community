/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Tag("module")
public class RunConfigurationModule implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(RunConfigurationModule.class);

  @NonNls private static final String ELEMENT = "module";
  @NonNls private static final String ATTRIBUTE = "name";

  private @Nullable ModulePointer myModulePointer;

  private final Project myProject;

  public RunConfigurationModule(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void readExternal(@NotNull Element element) {
    List<Element> modules = element.getChildren(ELEMENT);
    if (!modules.isEmpty()) {
      if (modules.size() > 1) {
        LOG.warn("Module serialized more than one time");
      }
      // we are unable to set 'null' module from 'not null' one
      String moduleName = modules.get(0).getAttributeValue(ATTRIBUTE);
      if (!StringUtil.isEmpty(moduleName)) {
        myModulePointer = ModulePointerManager.getInstance(myProject).create(moduleName);
      }
    }
  }

  @Override
  public void writeExternal(@NotNull Element parent) {
    Element prev = parent.getChild(ELEMENT);
    if (prev == null) {
      prev = new Element(ELEMENT);
      parent.addContent(prev);
    }
    prev.setAttribute(ATTRIBUTE, getModuleName());
  }

  public void init() {
    if (StringUtil.isEmptyOrSpaces(getModuleName())) {
      Module[] modules = getModuleManager().getModules();
      if (modules.length > 0) {
        setModule(modules[0]);
      }
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  @Transient
  public Module getModule() {
    return myModulePointer != null ? myModulePointer.getModule() : null;
  }

  @Nullable
  public Module findModule(@NotNull String moduleName) {
    if (myProject.isDisposed()) {
      return null;
    }
    return getModuleManager().findModuleByName(moduleName);
  }

  public void setModule(final Module module) {
    myModulePointer = module != null ? ModulePointerManager.getInstance(myProject).create(module) : null;
  }

  public void setModuleName(@Nullable String moduleName) {
    if (myModulePointer != null && !myModulePointer.getModuleName().equals(moduleName) || myModulePointer == null && moduleName != null) {
      myModulePointer = moduleName != null ? ModulePointerManager.getInstance(myProject).create(moduleName) : null;
    }
  }

  @Attribute("name")
  @NotNull
  public String getModuleName() {
    return myModulePointer != null ? myModulePointer.getModuleName() : "";
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  public void checkForWarning() throws RuntimeConfigurationException {
    final Module module = getModule();
    if (module != null) {
      if (ModuleRootManager.getInstance(module).getSdk() == null) {
        throw new RuntimeConfigurationWarning(ExecutionBundle.message("no.jdk.specified.for.module.warning.text", module.getName()));
      }
    }
    else {
      if (myModulePointer != null) {
        String moduleName = myModulePointer.getModuleName();
        if (ModuleManager.getInstance(myProject).getUnloadedModuleDescription(moduleName) != null) {
          throw new RuntimeConfigurationError(ExecutionBundle.message("module.is.unloaded.from.project.error.text", moduleName));
        }
        throw new RuntimeConfigurationError(ExecutionBundle.message("module.doesn.t.exist.in.project.error.text", moduleName));
      }
      throw new RuntimeConfigurationError(ExecutionBundle.message("module.not.specified.error.text"));
    }
  }
}
