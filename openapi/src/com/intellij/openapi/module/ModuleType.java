/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;

import javax.swing.*;

public abstract class ModuleType<T extends ModuleBuilder> {
  // predefined module types
  public static ModuleType JAVA;
  public static ModuleType EJB;
  public static ModuleType WEB;
  public static ModuleType J2EE_APPLICATION;

  private final String myId;

  protected ModuleType(String id) {
    myId = id;
  }

  public abstract T createModuleBuilder();

  public abstract String getName();
  public abstract String getDescription();
  public abstract Icon getBigIcon();
  public abstract Icon getNodeIcon(boolean isOpened);

  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, T moduleBuilder, ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  public final String getId() {
    return myId;
  }

  public boolean isJ2EE() {
    return false;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleType)) return false;

    final ModuleType moduleType = (ModuleType)o;

    if (myId != null ? !myId.equals(moduleType.myId) : moduleType.myId != null) return false;

    return true;
  }

  public int hashCode() {
    return myId != null ? myId.hashCode() : 0;
  }

  public String toString() {
    return getName();
  }
}
