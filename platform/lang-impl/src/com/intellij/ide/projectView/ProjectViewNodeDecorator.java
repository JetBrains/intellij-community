package com.intellij.ide.projectView;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;

/**
 * @author yole
 */
public interface ProjectViewNodeDecorator {
  ExtensionPointName<ProjectViewNodeDecorator> EP_NAME = ExtensionPointName.create("com.intellij.projectViewNodeDecorator");

  void decorate(ProjectViewNode node, PresentationData data);

  void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer);
}
