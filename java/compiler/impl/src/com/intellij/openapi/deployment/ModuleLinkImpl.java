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
package com.intellij.openapi.deployment;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ModuleLinkImpl extends ModuleLink {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.deployment.ModuleLink");
  @NonNls public static final String NAME_ATTRIBUTE_NAME = "name";
  @NonNls private static final String TEMP_ELEMENT_NAME = "temp";
  private Module myModule;
  private String myModuleName;
  private static final Map<PackagingMethod, String> methodToDescription = new HashMap<PackagingMethod, String>();

  static {
    methodToDescription.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescription.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.module.output"));
    methodToDescription.put(PackagingMethod.JAR_AND_COPY_FILE, CompilerBundle.message("packaging.method.description.jar.module.and.copy"));
    methodToDescription.put(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.jar.module.link.via.manifest.and.copy"));
    methodToDescription.put(PackagingMethod.INCLUDE_MODULE_IN_BUILD, CompilerBundle.message("packaging.method.description.include.module.in.build"));
  }

  public ModuleLinkImpl(@NotNull Module module, @NotNull Module parentModule) {
    super(parentModule);
    myModule = module;
    myModuleName = ModuleUtil.getModuleNameInReadAction(myModule);
  }

  public ModuleLinkImpl(String moduleName, @NotNull Module parentModule) {
    super(parentModule);
    myModuleName = moduleName;
  }

  private Module getModule(ModulesProvider provider) {
    if (myModule != null && myModule.isDisposed()) {
      myModule = null;
    }
    if (myModule == null) {
      myModule = provider.getModule(myModuleName);
    }
    return myModule;
  }

  @Nullable
  public Module getModule() {
    if (myModule != null && myModule.isDisposed()) {
      myModule = null;
    }
    if (myModule == null) {
      myModule = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        public Module compute() {
          return ModuleManager.getInstance(getParentModule().getProject()).findModuleByName(myModuleName);
        }
      });
    }
    return myModule;
  }

  public String toString() {
    return CompilerBundle.message("module.link.string.representation", getName(), getURI());
  }

  public boolean equalsIgnoreAttributes(ContainerElement otherElement) {
    return otherElement instanceof ModuleLink && Comparing.strEqual(((ModuleLink)otherElement).getName(), getName());
  }

  public String getPresentableName() {
    return getName();
  }

  public String getDescription() {
    final Module module = getModule();
    return module == null ? "" : module.getModuleType().getName();
  }

  public String getDescriptionForPackagingMethod(PackagingMethod method) {
    return methodToDescription.get(method);
  }

  public boolean resolveElement(ModulesProvider provider, final FacetsProvider facetsProvider) {
    return getModule(provider) != null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myModuleName = element.getAttributeValue(NAME_ATTRIBUTE_NAME);
    migratePackagingMethods();
  }

  private void migratePackagingMethods() {
    if (getPackagingMethod() == PackagingMethod.COPY_CLASSES) {
      setPackagingMethod(PackagingMethod.COPY_FILES);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.setAttribute(NAME_ATTRIBUTE_NAME, getName());
  }

  public String getName() {
    if (myModule == null) {
      return myModuleName;
    }
    else {
      return ModuleUtil.getModuleNameInReadAction(myModule);
    }
  }

  public ModuleLink clone() {
    ModuleLink moduleLink = new ModuleLinkImpl(getName(), getParentModule());
    Element temp = new Element(TEMP_ELEMENT_NAME);
    try {
      writeExternal(temp);
      moduleLink.readExternal(temp);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return moduleLink;
  }
}
