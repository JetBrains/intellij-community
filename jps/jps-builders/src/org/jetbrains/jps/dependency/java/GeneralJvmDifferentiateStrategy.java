// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.util.SmartList;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.DifferentiateStrategy;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.ServiceLoader;

import static org.jetbrains.jps.javac.Iterators.collect;

public final class GeneralJvmDifferentiateStrategy implements DifferentiateStrategy {
  
  private static final Iterable<JvmDifferentiateStrategy> ourExtensions = collect(
    ServiceLoader.load(JvmDifferentiateStrategy.class, GeneralJvmDifferentiateStrategy.class.getClassLoader()),
    new SmartList<>()
  );

  @Override
  public boolean isIncremental(DifferentiateContext context, Node<?, ?> affectedNode) {
    for (JvmDifferentiateStrategy extension : ourExtensions) {
      if (!extension.isIncremental(context, affectedNode)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter) {
    Utils future = new Utils(context, true);
    Utils present = new Utils(context, false);

    Difference.Specifier<JvmClass, JvmClass.Diff> classesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmClass.class), Graph.getNodesOfType(nodesAfter, JvmClass.class)
    );
    Difference.Specifier<JvmModule, JvmModule.Diff> modulesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmModule.class), Graph.getNodesOfType(nodesAfter, JvmModule.class)
    );

    if (!classesDiff.unchanged() || !modulesDiff.unchanged()) {
      for (JvmDifferentiateStrategy strategy : ourExtensions) {
        if (!strategy.processRemovedClasses(context, classesDiff.removed(), future, present)) {
          return false;
        }
        if (!strategy.processAddedClasses(context, classesDiff.added(), future, present)) {
          return false;
        }
        if (!strategy.processChangedClasses(context, classesDiff.changed(), future, present)) {
          return false;
        }

        if (!strategy.processRemovedModules(context, modulesDiff.removed(), future, present)) {
          return false;
        }
        if (!strategy.processAddedModules(context, modulesDiff.added(), future, present)) {
          return false;
        }
        if (!strategy.processChangedModules(context, modulesDiff.changed(), future, present)) {
          return false;
        }
      }
    }

    return true;
  }

}
