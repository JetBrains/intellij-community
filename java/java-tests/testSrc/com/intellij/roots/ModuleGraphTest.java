// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ModuleGraphTest extends ModuleRootManagerTestCase {
  public void testOuts() {
    Module a = createModule("a");
    Module b = createModule("b");
    addDependency(myModule, a);
    addDependency(a, b);

    Graph<Module> graph = ModuleManager.getInstance(getProject()).moduleGraph();
    List<Module> outs = ContainerUtil.collect(graph.getOut(a));
    assertEquals(1, outs.size());

    Set<Module> set = new HashSet<>();
    GraphAlgorithms.getInstance().collectOutsRecursively(graph, b, set);
    assertEquals(3, set.size());
  }

  protected void addDependency(Module module, Module a) {
    ModuleRootModificationUtil.addDependency(module, a, DependencyScope.COMPILE, true);
  }
}
