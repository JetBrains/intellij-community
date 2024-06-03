// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import com.intellij.openapi.diagnostic.Logger;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmTypeAlias;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.jps.javac.Iterators.*;

public final class KotlinSourceOnlyDifferentiateStrategy implements DifferentiateStrategy {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.dependency.kotlin.KotlinSourceOnlyDifferentiateStrategy");

  @Override
  public boolean differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter) {
    Delta delta = context.getDelta();
    if (!delta.isSourceOnly()) {
      return true;
    }

    Graph graph = context.getGraph();
    Set<NodeSource> baseSources = delta.getBaseSources();
    Utils present = new Utils(context.getGraph(), context.getParams().belongsToCurrentCompilationChunk());
    Set<Usage> affectedUsages = new HashSet<>();
    Set<JvmNodeReferenceID> baseNodes = new HashSet<>();

    for (JvmClass cls : flat(map(baseSources, s -> graph.getNodes(s, JvmClass.class)))) {

      if (!cls.isPrivate()) {
        for (ReferenceID id : present.withAllSubclasses(cls.getReferenceID())) {
          if (id instanceof JvmNodeReferenceID) {
            // potentially changed types can be used in kotlin's type inference in super classes
            affectedUsages.add(new ClassUsage(((JvmNodeReferenceID)id)));
          }
        }
      }

      KmDeclarationContainer container = KJvmUtils.getDeclarationContainer(cls);
      if (container != null) {
        if (container instanceof KmClass) {
          baseNodes.add(cls.getReferenceID());
          if (KJvmUtils.isSealed(container)) {
            for (NodeSource src : filter(unique(flat(map(present.directSubclasses(cls.getReferenceID()), present::getNodeSources))), s -> !baseSources.contains(s))) {
              LOG.debug("Sealed class is about to be recompiled => should be always compiled together with all its subclasses. Affecting  " + src);
              context.affectNodeSource(src);
            }
          }
        }
        List<KmTypeAlias> typeAliases = container.getTypeAliases();
        if (!typeAliases.isEmpty()) {
          String ownerName = KJvmUtils.getKotlinName(cls);
          if (ownerName != null) {
            for (KmTypeAlias alias : typeAliases) {
              // there can be dependencies in the kotlin super classes on potentially changed type aliases
              affectedUsages.add(new LookupNameUsage(ownerName, alias.getName()));
            }
          }
        }
      }
    }

    if (!affectedUsages.isEmpty()) {
      BooleanFunction<NodeSource> unmodifiedKtSources = s -> !baseSources.contains(s) && !isEmpty(filter(graph.getNodes(s, JvmClass.class), KJvmUtils::isKotlinNode));

      Iterable<NodeSource> supertypeSources = unique(flat(map(unique(filter(flat(map(baseNodes, present::allSupertypes)), id -> !baseNodes.contains(id))), present::getNodeSources)));

      for (NodeSource src : filter(supertypeSources, unmodifiedKtSources)) {
        if (!isEmpty(filter(flat(map(graph.getNodes(src), Node::getUsages)), affectedUsages::contains))) {
          LOG.debug("Parent Kotlin class in a class hierarchy is not marked for compilation, while it may be using a potentially changed type alias or has compiler-inferred types based on potentially changed types. Affecting  " + src);
          context.affectNodeSource(src);
        }
      }
    }

    return true;
  }

}
