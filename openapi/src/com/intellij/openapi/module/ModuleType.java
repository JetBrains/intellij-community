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
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class ModuleType<T extends ModuleBuilder> {
  // predefined module types
  public static ModuleType JAVA;
  public static ModuleType EMPTY;

  private final String myId;

  protected ModuleType(@NonNls String id) {
    myId = id;
  }

  public abstract T createModuleBuilder();

  public abstract String getName();
  public abstract String getDescription();
  public abstract Icon getBigIcon();
  public abstract Icon getNodeIcon(boolean isOpened);

  public String getProjectType() {
    return getName();
  }

  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, T moduleBuilder, ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  public final String getId() {
    return myId;
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
    JAVA = instantiate("com.intellij.openapi.module.JavaModuleType");
    EMPTY = instantiate("com.intellij.openapi.module.EmptyModuleType");
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
