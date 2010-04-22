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

package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public class ModuleNode extends PackageDependenciesNode {
  private final Module myModule;

  public ModuleNode(Module module) {
    myModule = module;    
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }

  @Override
  public boolean canNavigate() {
    return myModule != null && !myModule.isDisposed();
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void navigate(boolean focus) {
    ProjectSettingsService.getInstance(myModule.getProject()).openModuleSettings(myModule);
  }

  public Icon getOpenIcon() {
    return myModule == null ? null : myModule.getModuleType().getNodeIcon(true);
  }

  public Icon getClosedIcon() {
    return myModule == null ? null : myModule.getModuleType().getNodeIcon(false);
  }

  public String toString() {
    return myModule == null ? AnalysisScopeBundle.message("unknown.node.text") : myModule.getName();
  }

  public String getModuleName() {
    return myModule.getName();
  }

  public Module getModule() {
    return myModule;
  }

  public int getWeight() {
    return 1;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof ModuleNode)) return false;

    final ModuleNode moduleNode = (ModuleNode)o;

    return Comparing.equal(myModule, moduleNode.myModule);
  }

  public int hashCode() {
    return myModule == null ? 0 : myModule.hashCode();
  }


  public boolean isValid() {
    return myModule != null && !myModule.isDisposed();
  }
}
