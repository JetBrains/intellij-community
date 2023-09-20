// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.BackDependencyIndex;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.impl.StringReferenceID;
import org.jetbrains.jps.javac.Iterators;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class Utils {
  public static final String OBJECT_CLASS_NAME = "java/lang/Object";

  @NotNull
  private final Graph myGraph;
  
  @Nullable
  private final Graph myDelta;

  private final @NotNull BackDependencyIndex myDirectSubclasses;
  private final Set<NodeSource> myCompiledSources;

  public Utils(Graph graph, @Nullable Graph delta) {
    myGraph = graph;
    myDelta = delta;
    myDirectSubclasses = Objects.requireNonNull(graph.getIndex(SubclassesIndex.NAME));
    Iterable<NodeSource> deltaSources = delta != null? delta.getSources() : null;
    myCompiledSources = deltaSources instanceof Set? (Set<NodeSource>)deltaSources : Iterators.collect(deltaSources, new HashSet<>());
  }

  public boolean isCompiled(@NotNull NodeSource src) {
    return myCompiledSources.contains(src);
  }
  
  public Iterable<JvmClass> getClassesByName(@NotNull String name) {
    return getNodes(new StringReferenceID(name), JvmClass.class);
  }

  public Iterable<JvmModule> getModulesByName(@NotNull String name) {
    return getNodes(new StringReferenceID(name), JvmModule.class);
  }

  /**
   * @param id
   * @return all nodes with the given ReferenceID. Nodes in the returned collection will have the same ReferenceID, but may be associated with different sources
   */
  public <T extends JVMClassNode<T, ?>> Iterable<T> getNodes(@NotNull ReferenceID id, Class<T> selector) {
    Iterable<T> allNodes;
    if (myDelta != null) {
      Set<NodeSource> deltaSources = Iterators.collect(myDelta.getSources(id), new HashSet<>());
      allNodes = Iterators.flat(
        Iterators.flat(Iterators.map(deltaSources, src -> myDelta.getNodes(src, selector))), Iterators.flat(Iterators.map(Iterators.filter(myGraph.getSources(id), src -> !deltaSources.contains(src)), src -> myGraph.getNodes(src, selector)))
      );
    }
    else {
      allNodes = Iterators.flat(Iterators.map(myGraph.getSources(id), src -> myGraph.getNodes(src, selector)));
    }
    return Iterators.unique(Iterators.filter(allNodes, n -> id.equals(n.getReferenceID())));
  }

  public void traverseSubclasses(ReferenceID from, Consumer<? super ReferenceID> acc) {
    traverseSubclasses(from, id -> {
      acc.accept(from);
      return Boolean.TRUE;
    });
  }

  public void traverseSubclasses(ReferenceID from, Function<? super ReferenceID, Boolean> f) {
    new Object() {
      final Set<ReferenceID> visited = new HashSet<>();

      void traverse(ReferenceID from, Function<? super ReferenceID, Boolean> f) {
        if (visited.add(from)) {
          if (f.apply(from)) {
            for (ReferenceID sub : myDirectSubclasses.getDependencies(from)) {
              traverse(sub, f);
            }
          }
        }

      }
    }.traverse(from, f);
  }

  public Iterable<String> allDirectSupertypes(String className) {
    return Iterators.unique(Iterators.flat(Iterators.map(getClassesByName(className), cl -> cl.getSuperTypes())));
  }

  public Set<String> collectAllSupertypes(String className, Set<String> acc) {
    for (String st : allDirectSupertypes(className)) {
      if (acc.add(st)) {
        collectAllSupertypes(st, acc);
      }
    }
    return acc;
  }

}
