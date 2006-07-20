/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.javaee.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.javaee.J2EEBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.HashMap;

public class ModuleLinkImpl extends ModuleLink {
  private static final Logger LOG = Logger.getInstance("#com.intellij.javaee.module.ModuleLink");
  @NonNls private static final String NAME_ATTRIBUTE_NAME = "name";
  @NonNls private static final String TEMP_ELEMENT_NAME = "temp";
  private Module myModule;
  private String myModuleName;
  private static Map<J2EEPackagingMethod, String> methodToDescription = new HashMap<J2EEPackagingMethod, String>();

  static {
    methodToDescription.put(J2EEPackagingMethod.DO_NOT_PACKAGE, J2EEBundle.message("packaging.method.description.do.not.package"));
    methodToDescription.put(J2EEPackagingMethod.COPY_FILES, J2EEBundle.message("packaging.method.description.copy.module.output"));
    methodToDescription.put(J2EEPackagingMethod.JAR_AND_COPY_FILE, J2EEBundle.message("packaging.method.description.jar.module.and.copy"));
    methodToDescription.put(J2EEPackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST, J2EEBundle.message("packaging.method.description.jar.module.link.via.manifest.and.copy"));
    methodToDescription.put(J2EEPackagingMethod.INCLUDE_MODULE_IN_BUILD, J2EEBundle.message("packaging.method.description.include.module.in.build"));
  }

  public ModuleLinkImpl(Module module, Module parentModule) {
    super(parentModule);
    LOG.assertTrue(module != null);
    myModule = module;
  }

  public ModuleLinkImpl(String moduleName, Module parentModule) {
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

  public @Nullable Module getModule() {
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
    return J2EEBundle.message("module.link.string.representation", getName(), getURI());
  }

  public boolean equalsIgnoreAttributes(ContainerElement otherElement) {
    if (!(otherElement instanceof ModuleLink)) return false;
    return Comparing.strEqual(((ModuleLink)otherElement).getName(), getName());
  }

  public String getId() {
    return getId(getModule());
  }

  public boolean hasId(String id) {
    return hasId(getModule(), id);
  }

  public String getPresentableName() {
    return getName();
  }

  public String getDescription() {
    final Module module = getModule();
    return module == null ? "" : module.getModuleType().getName();
  }

  public String getDescriptionForPackagingMethod(J2EEPackagingMethod method) {
    return methodToDescription.get(method);
  }

  public boolean resolveElement(ModulesProvider provider) {
    return getModule(provider) != null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myModuleName = element.getAttributeValue(NAME_ATTRIBUTE_NAME);
    migratePackagingMethods();
  }

  private void migratePackagingMethods() {
    if (getPackagingMethod() == J2EEPackagingMethod.COPY_CLASSES) {
      setPackagingMethod(J2EEPackagingMethod.COPY_FILES);
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
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return myModule.getName();
        }
      });
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
