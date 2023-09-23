// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DocumentationOrderRootTypeUIFactory implements OrderRootTypeUIFactory {

  @Override
  public @Nullable SdkPathEditor createPathEditor(Sdk sdk) {
    return null;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.JavaDocFolder;
  }

  @Override
  public String getNodeText() {
    return ProjectBundle.message("library.docs.node");
  }
}
