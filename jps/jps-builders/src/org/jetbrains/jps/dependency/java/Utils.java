// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.javac.Iterators;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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

  @Nullable
  public String getNodeName(ReferenceID id) {
    if (id instanceof JvmNodeReferenceID) {
      return ((JvmNodeReferenceID)id).getNodeName();
    }
    Iterable<JVMClassNode> nodes = getNodes(id, JVMClassNode.class);
    for (var node : nodes) {
      return node.getName();
    }
    return null;
  }

  // test if a ClassRepr is a SAM interface
  public boolean isLambdaTarget(ReferenceID classId) {
    for (JvmClass cls : getNodes(classId, JvmClass.class)) {
      if (cls.isInterface()) {
        int amFound = 0;
        for (JvmMethod method : allMethodsRecursively(cls)) {
          if (method.isAbstract() && ++amFound > 1) {
            break;
          }
        }
        if (amFound == 1) {
          return true;
        }
      }
    }
    return false;
  }

  // return all methods of this class including all inherited methods recursively
  public Iterable<JvmMethod> allMethodsRecursively(JvmClass cls) {
    return Iterators.flat(collectRecursively(cls, c -> c.getMethods()));
  }

  private <T> Iterable<T> collectRecursively(JvmClass cls, Function<? super JvmClass, ? extends T> mapper) {
    return Iterators.map(Iterators.recurse(cls, c -> Iterators.flat(Iterators.map(c.getSuperTypes(), st -> getClassesByName(st))), true), mapper::apply);
  }


  /**
   * @param id a node reference ID
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

  public Iterable<JvmNodeReferenceID> allDirectSupertypes(JvmNodeReferenceID classId) {
    return Iterators.unique(Iterators.flat(Iterators.map(getNodes(classId, JvmClass.class), cl -> Iterators.map(cl.getSuperTypes(), st -> new JvmNodeReferenceID(st)))));
  }

  public Iterable<JvmNodeReferenceID> allSupertypes(JvmNodeReferenceID classId) {
    //return Iterators.recurseDepth(className, s -> allDirectSupertypes(s), false);
    return Iterators.recurse(classId, s -> allDirectSupertypes(s), false);
  }

  @NotNull
  public Iterable<ReferenceID> withAllSubclasses(ReferenceID from) {
    return Iterators.recurse(from, myDirectSubclasses::getDependencies, true);
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
        for (ReferenceID id : withAllSubclasses(ownerID)) {
          if (id instanceof JvmNodeReferenceID && !id.equals(ownerID)) {
            propagated.add(id);
          }
        }
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
