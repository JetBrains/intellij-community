package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;

import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface RendererProvider extends ApplicationComponent{
  Renderer createRenderer(String category);

  List<String> getAvailableNodeRenderers();

  UnnamedConfigurable getRendererConfigurable(Project project, NodeRenderer renderer);

  boolean isSingle(NodeRenderer renderer);
}
