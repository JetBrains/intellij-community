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

package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
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

  private Module myModule = null;

  @Attribute("name")
  private String myModuleName;

  private final Project myProject;

  public RunConfigurationModule(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public void readExternal(@NotNull Element element) {
    List<Element> modules = element.getChildren(ELEMENT);
    if (!modules.isEmpty()) {
      if (modules.size() > 1) {
        LOG.warn("Module serialized more than one time");
      }
      // we are unable to set 'null' module from 'not null' one
      String moduleName = modules.get(0).getAttributeValue(ATTRIBUTE);
      if (!StringUtil.isEmpty(moduleName)) {
        myModuleName = moduleName;
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
    if (myModuleName != null) { //caching
      myModule = findModule(myModuleName);
    }
    if (myModule != null && myModule.isDisposed()) {
      myModule = null;
    }
    return myModule;
  }

  @Nullable
  public Module findModule(final String moduleName) {
    if (myProject.isDisposed()) {
      return null;
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      @Override
      public Module compute() {
        return getModuleManager().findModuleByName(moduleName);
      }
    });
  }

  public void setModule(final Module module) {
    myModule = module;
    myModuleName = module != null ? module.getName() : null;
  }

  public String getModuleName() {
    return StringUtil.notNullize(myModuleName);
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
      if (myModuleName != null) {
        //noinspection SpellCheckingInspection
        throw new RuntimeConfigurationError(ExecutionBundle.message("module.doesn.t.exist.in.project.error.text", myModuleName));
      }
      throw new RuntimeConfigurationError(ExecutionBundle.message("module.not.specified.error.text"));
    }
  }
}
