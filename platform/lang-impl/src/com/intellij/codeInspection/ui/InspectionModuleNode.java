/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;

import javax.swing.*;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class InspectionModuleNode extends InspectionTreeNode{
  private final Module myModule;
  public InspectionModuleNode(final Module module) {
    super(module);
    myModule = module;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return myModule.isDisposed() ? null : ModuleType.get(myModule).getIcon();
  }

  public String getName(){
    return myModule.getName();
  }

  public String toString() {
    return getName();
  }
}
