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
package com.intellij.openapi.module.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ModuleTypeManagerImpl extends ModuleTypeManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleTypeManagerImpl");

  private final LinkedHashMap<ModuleType, Boolean> myModuleTypes = new LinkedHashMap<>();

  public ModuleTypeManagerImpl() {
    registerModuleType(getDefaultModuleType(), true);
    for (ModuleTypeEP ep : ModuleTypeEP.EP_NAME.getExtensions()) {
      if (ep.id == null) {
        LOG.error("'id' attribute isn't specified for <moduleType implementationClass='" + ep.implementationClass + "'> extension");
      }
    }
  }

  @Override
  public void registerModuleType(ModuleType type) {
    registerModuleType(type, false);
  }

  @Override
  public void registerModuleType(ModuleType type, boolean classpathProvider) {
    for (ModuleType oldType : myModuleTypes.keySet()) {
      if (oldType.getId().equals(type.getId())) {
        LOG.error("Trying to register a module type that clashes with existing one. Old=" + oldType + ", new = " + type);
        return;
      }
    }

    myModuleTypes.put(type, classpathProvider);
  }

  @Override
  public ModuleType[] getRegisteredTypes() {
    List<ModuleType> result = new ArrayList<>(myModuleTypes.keySet());
    for (ModuleTypeEP moduleTypeEP : Extensions.getExtensions(ModuleTypeEP.EP_NAME)) {
      result.add(moduleTypeEP.getModuleType());
    }

    return result.toArray(new ModuleType[result.size()]);
  }

  @Override
  public ModuleType findByID(String moduleTypeID) {
    if (moduleTypeID == null) return getDefaultModuleType();
    for (ModuleType type : myModuleTypes.keySet()) {
      if (type.getId().equals(moduleTypeID)) {
        return type;
      }
    }
    for (ModuleTypeEP ep : Extensions.getExtensions(ModuleTypeEP.EP_NAME)) {
      if (moduleTypeID.equals(ep.id)) {
        return ep.getModuleType();
      }
    }

    return new UnknownModuleType(moduleTypeID, getDefaultModuleType());
  }

  @Override
  public boolean isClasspathProvider(final ModuleType moduleType) {
    for (ModuleTypeEP ep : Extensions.getExtensions(ModuleTypeEP.EP_NAME)) {
      if (moduleType.getId().equals(ep.id)) {
        return ep.classpathProvider;
      }
    }

    final Boolean provider = myModuleTypes.get(moduleType);
    return provider != null && provider.booleanValue();
  }

  @Override
  public ModuleType getDefaultModuleType() {
    return EmptyModuleType.getInstance();
  }
}
