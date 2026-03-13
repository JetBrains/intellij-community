// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.Delta;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.DifferentiateParameters;
import org.jetbrains.jps.dependency.DifferentiateStrategy;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.LogConsumer;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.find;
import static org.jetbrains.jps.util.Iterators.flat;
import static org.jetbrains.jps.util.Iterators.map;

public final class GeneralJvmDifferentiateStrategy implements DifferentiateStrategy {

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
        private final Map<ReferenceID, Boolean> traversed = new HashMap<>();

        private boolean isMarked(NodeSource src) {
          return isMarked(Collections.singleton(src));
        }

        private boolean isMarked(Iterable<NodeSource> sources) {
          return find(sources, baseSources::contains) != null;
        }

        boolean traverse(JvmClass cl, boolean isRoot) {
          if (!isRoot) {
            Boolean cached = traversed.get(cl.getReferenceID());
            if (cached != null) {
              return cached;
            }
            traversed.put(cl.getReferenceID(), Boolean.FALSE); // default value for cycle safety; overwritten with the actual result below
          }
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
            LogConsumer logger = context.getParams().logConsumer();
            for (NodeSource source : filter(nodeSources, s -> !isMarked(s) && inCurrentChunk.test(s))) {
              logger.consume(
                "Intermediate class in a class hierarchy is not marked for compilation, while one of its subclasses and superclasses are going to be recompiled. Affecting  " + source.toString()
              );
              context.affectNodeSource(source);
            }
          }
          boolean result = parentsMarked || isMarked(nodeSources);
          traversed.put(cl.getReferenceID(), result);
          return result;
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
      Utils currentChunkPresent = new Utils(context.getGraph(), DifferentiateParameters.affectableInCurrentChunk(context.getParams()));
      for (JvmDifferentiateStrategy strategy : ourExtensions) {
        if (!strategy.processNodesWithErrors(context, errNodes, currentChunkPresent)) {
          return false;
        }
      }
    }

    return true;
  }

}
