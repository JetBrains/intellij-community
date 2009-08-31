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
