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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;

import javax.swing.*;
import java.awt.*;

/**
 * TODO: remove it together witrh DependenciesEditor
 */
public class ModuleChooserElement implements ElementsChooser.ElementProperties{
  private final String myName;
  private final Module myModule;
  private ModuleOrderEntry myOrderEntry;

  public ModuleChooserElement(Module module, ModuleOrderEntry orderEntry) {
    myModule = module;
    myOrderEntry = orderEntry;
    myName = module != null? module.getName() : orderEntry.getModuleName();
  }

  public Module getModule() {
    return myModule;
  }

  public ModuleOrderEntry getOrderEntry() {
    return myOrderEntry;
  }

  public void setOrderEntry(ModuleOrderEntry orderEntry) {
    myOrderEntry = orderEntry;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    if (myModule != null) {
      return myModule.getModuleType().getNodeIcon(false);
    }
    else {
      return CellAppearanceUtils.INVALID_ICON;
    }
  }

  public Color getColor() {
    return myModule == null ? Color.RED : null;
  }

  public String toString() {
    return getName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleChooserElement)) return false;

    final ModuleChooserElement chooserElement = (ModuleChooserElement)o;

    if (!myName.equals(chooserElement.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }
}
