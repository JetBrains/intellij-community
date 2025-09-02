// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jetbrains.jps.util.Iterators.*;

public final class GeneralJvmDifferentiateStrategy implements DifferentiateStrategy {
  private static final Logger LOG = Logger.getLogger("#org.jetbrains.jps.dependency.java.GeneralJvmDifferentiateStrategy");

  private static final Iterable<JvmDifferentiateStrategy> ourExtensions = collect(
    ServiceLoader.load(JvmDifferentiateStrategy.class),
    new ArrayList<>()
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
  public boolean
  differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter, Iterable<Node<?, ?>> nodesWithErrors) {
    Utils future = new Utils(context, true);
    Utils present = new Utils(context, false);

    Delta delta = context.getDelta();
    if (delta.isSourceOnly()) {
      new Object() {
        private final Predicate<? super NodeSource> inCurrentChunk = context.getParams().belongsToCurrentCompilationChunk();
        private final Set<NodeSource> baseSources = delta.getBaseSources();

        private boolean isMarked(NodeSource src) {
          return isMarked(Collections.singleton(src));
        }

        private boolean isMarked(Iterable<NodeSource> sources) {
          return find(sources, baseSources::contains) != null;
        }

        boolean traverse(JvmClass cl, boolean isRoot) {
          boolean parentsMarked = false;
          if (cl.isLibrary()) {
            return parentsMarked;
          }
          for (JvmClass superCl : present.allDirectSupertypes(cl)) {
            parentsMarked |= traverse(superCl, false);
          }
          if (isRoot) {
            return true;
          }
          Iterable<NodeSource> nodeSources = present.getNodeSources(cl.getReferenceID());
          if (parentsMarked) {
            for (NodeSource source : filter(nodeSources, s -> !isMarked(s) && inCurrentChunk.test(s))) {
              LOG.log(Level.FINE, "Intermediate class in a class hierarchy is not marked for compilation, while one of its subclasses and superclasses are going to be recompiled. Affecting  " + source.toString());
              context.affectNodeSource(source);
            }
          }
          return parentsMarked || isMarked(nodeSources);
        }

        void markSources() {
          Graph graph = context.getGraph();
          for (JvmClass cls : flat(map(baseSources, s -> graph.getNodes(s, JvmClass.class)))) {
            traverse(cls, true);
          }
        }
      }.markSources();
    }

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

    List<JVMClassNode<?, ?>> errNodes = collect(filter(map(nodesWithErrors, n -> n instanceof JVMClassNode? (JVMClassNode<?, ?>)n : null), Objects::nonNull), new ArrayList<>());
    if (!errNodes.isEmpty()) {
      for (JvmDifferentiateStrategy strategy : ourExtensions) {
        if (!strategy.processNodesWithErrors(context, errNodes, present)) {
          return false;
        }
      }
    }

    return true;
  }

}
