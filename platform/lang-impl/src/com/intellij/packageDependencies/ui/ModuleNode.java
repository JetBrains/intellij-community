/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.NavigatableWithText;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public class ModuleNode extends PackageDependenciesNode implements NavigatableWithText {
  private final Module myModule;

  public ModuleNode(Module module) {
    super(module.getProject());
    myModule = module;
  }

  @Override
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

  @Override
  public Icon getIcon() {
    return myModule == null || myModule.isDisposed() ? super.getIcon() : ModuleType.get(myModule).getIcon();
  }

  @Override
  public String toString() {
    return myModule == null ? AnalysisScopeBundle.message("unknown.node.text") : myModule.getName();
  }

  public String getModuleName() {
    return myModule.getName();
  }

  public Module getModule() {
    return myModule;
  }

  @Override
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

  @Override
  public int hashCode() {
    return myModule == null ? 0 : myModule.hashCode();
  }

  @Override
  public boolean isValid() {
    return myModule != null && !myModule.isDisposed();
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return ActionsBundle.message("action.ModuleSettings.navigate");
  }
}
