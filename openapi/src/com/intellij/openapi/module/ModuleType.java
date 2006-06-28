/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

public abstract class ModuleType<T extends ModuleBuilder> {
  // predefined module types
  public static ModuleType JAVA;
  public static ModuleType EJB;
  public static ModuleType WEB;
  public static ModuleType J2EE_APPLICATION;

  private final String myId;
  private final List<ModuleWizardStepsProvider<T>> myWizardStepProviders = new ArrayList<ModuleWizardStepsProvider<T>>();

  protected ModuleType(@NonNls String id) {
    myId = id;
  }

  public abstract T createModuleBuilder();

  public abstract String getName();
  public abstract String getDescription();
  public abstract Icon getBigIcon();
  public abstract Icon getNodeIcon(boolean isOpened);

  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, T moduleBuilder, ModulesProvider modulesProvider) {
    ModuleWizardStep[] result = ModuleWizardStep.EMPTY_ARRAY;
    for (ModuleWizardStepsProvider<T> provider: myWizardStepProviders) {
      ArrayUtil.mergeArrays(result, provider.createWizardSteps(wizardContext, moduleBuilder, modulesProvider), ModuleWizardStep.class);
    }
    return result;
  }

  public void registerWizardStepsProvider(ModuleWizardStepsProvider<T> provider) {
    myWizardStepProviders.add(provider);
  }

  public final String getId() {
    return myId;
  }

  public boolean isJ2EE() {
    return false;
  }

  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleType)) return false;

    final ModuleType moduleType = (ModuleType)o;

    if (myId != null ? !myId.equals(moduleType.myId) : moduleType.myId != null) return false;

    return true;
  }

  public final int hashCode() {
    return myId != null ? myId.hashCode() : 0;
  }

  public String toString() {
    return getName();
  }

  static {
    WEB = instantiate("com.intellij.openapi.module.WebModuleType");
    JAVA = instantiate("com.intellij.openapi.module.JavaModuleType");
    EJB = instantiate("com.intellij.openapi.module.EjbModuleType");
    J2EE_APPLICATION = instantiate("com.intellij.openapi.module.J2EEApplicationModuleType");
  }

  private static ModuleType instantiate(String className) {
    try {
      return (ModuleType)Class.forName(className).newInstance();
    }
    catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
