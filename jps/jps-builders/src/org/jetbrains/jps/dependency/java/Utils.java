// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.javac.Iterators;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Utils {
  public static final String OBJECT_CLASS_NAME = "java/lang/Object";

  private final @NotNull Graph myGraph;
  
  private final @Nullable Graph myDelta;

  private final @NotNull BackDependencyIndex myDirectSubclasses;

  public Utils(Graph graph, @Nullable Graph delta) {
    myGraph = graph;
    myDelta = delta;
    myDirectSubclasses = Objects.requireNonNull(graph.getIndex(SubclassesIndex.NAME));
  }

  public Iterable<JvmClass> getClassesByName(@NotNull String name) {
    return getNodes(new JvmNodeReferenceID(name), JvmClass.class);
  }

  public Iterable<JvmModule> getModulesByName(@NotNull String name) {
    return getNodes(new JvmNodeReferenceID(name), JvmModule.class);
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

  public Set<ReferenceID> collectSubclassesWithoutField(String className, String fieldName) {
    return collectSubclassesWithoutMember(className, f -> fieldName.equals(f.getName()), cls -> cls.getFields());
  }

  public Set<ReferenceID> collectSubclassesWithoutMethod(String className, JvmMethod method) {
    return collectSubclassesWithoutMember(className, m -> method.equals(m), cls -> cls.getMethods());
  }

  // propagateMemberAccess
  private <T extends ProtoMember> Set<ReferenceID> collectSubclassesWithoutMember(String className, Predicate<? super T> isSame, Function<JvmClass, Iterable<T>> membersGetter) {
    Set<ReferenceID> result = new HashSet<>();
    JvmNodeReferenceID fromClassID = new JvmNodeReferenceID(className);
    
    traverseSubclasses(fromClassID, clsID -> {
      if (clsID instanceof JvmNodeReferenceID && !clsID.equals(fromClassID)) {
        if (Iterators.isEmpty(Iterators.filter(getClassesByName(((JvmNodeReferenceID)clsID).getNodeName()), subCls -> Iterators.isEmpty(Iterators.filter(membersGetter.apply(subCls), isSame::test))))) {
          // stop further traversal, if nodes corresponding to the subclassName contain matching member
          return false;
        }
        result.add(clsID);
      }
      return true;
    });
    
    return result;
  }

  public boolean incrementalDecision(DifferentiateContext context, JvmClass owner, @Nullable JvmField field) {
    // Public branch --- hopeless

    if ((field != null? field : owner).isPublic()) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    // Protected branch

    Set<NodeSource> toRecompile = new HashSet<>();
    if ((field != null? field : owner).isProtected()) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: " + owner.getName());

      Set<ReferenceID> propagated;
      if (field != null) {
        propagated = collectSubclassesWithoutField(owner.getName(), field.getName());
      }
      else {
        propagated = new HashSet<>();
        JvmNodeReferenceID ownerID = owner.getReferenceID();
        traverseSubclasses(ownerID, id -> {
          if (id instanceof JvmNodeReferenceID && !id.equals(ownerID)) {
            propagated.add(id);
          }
        });
      }
      Iterators.collect(Iterators.flat(Iterators.map(propagated, id -> myGraph.getSources(id))), toRecompile);
    }

    // Package-local branch

    String packageName = owner.getPackageName();
    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: " + packageName);

    Iterators.collect(Iterators.flat(Iterators.map(
      Iterators.filter(myGraph.getRegisteredNodes(), id -> id instanceof JvmNodeReferenceID && packageName.equals(JvmClass.getPackageName(((JvmNodeReferenceID)id).getNodeName()))),
      id -> myGraph.getSources(id)
    )), toRecompile);

    for (NodeSource source : Iterators.filter(toRecompile, s -> !context.isCompiled(s) && !context.getDelta().getDeletedSources().contains(s))) {
      context.affectNodeSource(source);
    }

    return true;
  }


  private void debug(String message) {
    // todo
  }
}
