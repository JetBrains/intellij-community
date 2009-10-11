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
package com.intellij.cyclicDependencies.ui;

import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiFile;
import com.intellij.analysis.AnalysisScopeBundle;

import javax.swing.*;
import java.util.Set;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CycleNode extends PackageDependenciesNode{
  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
  }

  public void addFile(PsiFile file, boolean isMarked) {
    super.addFile(file, isMarked);
  }

  public Icon getOpenIcon() {
    return super.getOpenIcon();
  }

  public Icon getClosedIcon() {
    return super.getClosedIcon();
  }

  public int getWeight() {
    return super.getWeight();
  }

  public String toString() {
    return AnalysisScopeBundle.message("cyclic.dependencies.tree.cycle.node.text");
  }

}
