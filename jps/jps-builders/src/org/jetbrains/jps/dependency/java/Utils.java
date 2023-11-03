// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.BackDependencyIndex;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.javac.Iterators;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Utils {

  private final @NotNull Graph myGraph;
  
  private final @Nullable Graph myDelta;

  private final @NotNull BackDependencyIndex myDirectSubclasses;

  public Utils(Graph graph, @Nullable Graph delta) {
    myGraph = graph;
    myDelta = delta;
    myDirectSubclasses = Objects.requireNonNull(graph.getIndex(SubclassesIndex.NAME));
  }

  public Iterable<NodeSource> getNodeSources(ReferenceID nodeId) {
    Iterable<NodeSource> sources = myDelta != null? myDelta.getSources(nodeId) : null;
    return !Iterators.isEmpty(sources)? sources : myGraph.getSources(nodeId);
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
    return Iterators.flat(
      Iterators.map(Iterators.recurse(cls, c -> Iterators.flat(Iterators.map(c.getSuperTypes(), st -> getClassesByName(st))), true), c -> c.getMethods())
    );
  }

  /**
   * @param id a node reference ID
   * @return all nodes with the given ReferenceID. Nodes in the returned collection will have the same ReferenceID, but may be associated with different sources
   */
  public <T extends JVMClassNode<T, ?>> Iterable<T> getNodes(@NotNull ReferenceID id, Class<T> selector) {
    if (id instanceof JvmNodeReferenceID && "".equals(((JvmNodeReferenceID)id).getNodeName())) {
      return Collections.emptyList();
    }
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

  @NotNull
  public Iterable<ReferenceID> allSubclasses(ReferenceID from) {
    return Iterators.recurse(from, myDirectSubclasses::getDependencies, false);
  }

  public Set<JvmNodeReferenceID> collectSubclassesWithoutField(JvmNodeReferenceID classId, String fieldName) {
    return collectSubclassesWithoutMember(classId, f -> fieldName.equals(f.getName()), cls -> cls.getFields());
  }

  public Set<JvmNodeReferenceID> collectSubclassesWithoutMethod(JvmNodeReferenceID classId, JvmMethod method) {
    return collectSubclassesWithoutMember(classId, m -> method.isSame(m), cls -> cls.getMethods());
  }

  // propagateMemberAccess
  private <T extends ProtoMember> Set<JvmNodeReferenceID> collectSubclassesWithoutMember(JvmNodeReferenceID classId, Predicate<? super T> isSame, Function<JvmClass, Iterable<T>> membersGetter) {
    Predicate<ReferenceID> containsMember = id -> Iterators.isEmpty(Iterators.filter(getNodes(id, JvmClass.class), cls -> Iterators.isEmpty(Iterators.filter(membersGetter.apply(cls), isSame::test))));
    //stop further traversal, if nodes corresponding to the subclassName contain matching member
    Iterable<JvmNodeReferenceID> result = getNodesData(
      (ReferenceID)classId,
      id -> myDirectSubclasses.getDependencies(id),
      id -> id instanceof JvmNodeReferenceID && !containsMember.test(id)? (JvmNodeReferenceID)id : null,
      Objects::nonNull,
      false
    );
    return Iterators.collect(Iterators.filter(result, Iterators.notNullFilter()), new HashSet<>());
  }

  public Iterable<Pair<JvmClass, JvmField>> getOverriddenFields(JvmClass fromCls, JvmField field) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmField>>> dataGetter = cl -> Iterators.collect(
      Iterators.map(Iterators.filter(cl.getFields(), f -> Objects.equals(f.getName(), field.getName()) && isVisibleInHierarchy(cl, f, fromCls)), ff -> Pair.create(cl, ff)),
      new SmartList<>()
    );
    return Iterators.flat(
      getNodesData(fromCls, cl -> Iterators.flat(Iterators.map(cl.getSuperTypes(), st -> getClassesByName(st))), dataGetter, result -> Iterators.isEmpty(result), false)
    );
  }

  public Iterable<Pair<JvmClass, JvmMethod>> getOverriddenMethods(JvmClass fromCls, Predicate<JvmMethod> searchCond) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmMethod>>> dataGetter = cl -> Iterators.collect(
      Iterators.map(Iterators.filter(cl.getMethods(), m -> searchCond.test(m) && isVisibleInHierarchy(cl, m, fromCls)), mm -> Pair.create(cl, mm)),
      new SmartList<>()
    );
    return Iterators.flat(
      getNodesData(fromCls, cl -> Iterators.flat(Iterators.map(cl.getSuperTypes(), st -> getClassesByName(st))), dataGetter, result -> Iterators.isEmpty(result), false)
    );
  }

  public Iterable<Pair<JvmClass, JvmMethod>> getOverridingMethods(JvmClass fromCls, JvmMethod method, Predicate<JvmMethod> searchCond) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmMethod>>> dataGetter = cl -> isVisibleInHierarchy(fromCls, method, cl)? Iterators.collect(
      Iterators.map(Iterators.filter(cl.getMethods(), searchCond::test), mm -> Pair.create(cl, mm)),
      new SmartList<>()
    ) : Collections.emptyList();
    return Iterators.flat(
      getNodesData(fromCls, cl -> Iterators.flat(Iterators.map(myDirectSubclasses.getDependencies(cl.getReferenceID()), st -> getNodes(st, JvmClass.class))), dataGetter, result -> Iterators.isEmpty(result), false)
    );
  }

   /*
   Traverse nodes starting from the given node and collect node-related data fetched with the given dataGetter.
   Further traversal for the current "subtree" stops, if the continuationCon predicate is 'false' for the dataGetter's result obtained on the subtree's root.
   Collected data is stored to the specified container
   */
  private static <N, V> Iterable<V> getNodesData(
    N fromNode, Function<? super N, ? extends Iterable<? extends N>> step, Function<N, V> dataGetter, Predicate<V> continuationCond, boolean includeHead
  ) {
    Function<N, V> mapper = cachingFunction(dataGetter);
    return Iterators.map(Iterators.recurseDepth(fromNode, node -> fromNode.equals(node) || continuationCond.test(mapper.apply(node))? step.apply(node): Collections.emptyList(), includeHead), mapper::apply);
  }

  public boolean hasOverriddenMethods(JvmClass cls, JvmMethod method) {
    return !Iterators.isEmpty(getOverriddenMethods(cls, method::isSameByJavaRules)) || inheritsFromLibraryClass(cls) /*assume the method can override some method from the library*/;
  }

  boolean isFieldVisible(final JvmClass cls, final JvmField field) {
    return !Iterators.isEmpty(Iterators.filter(cls.getFields(), field::isSame)) || !Iterators.isEmpty(getOverriddenFields(cls, field));
  }
  
  boolean isMethodVisible(final JvmClass cls, final JvmMethod method) {
    return !Iterators.isEmpty(Iterators.filter(cls.getMethods(), method::isSameByJavaRules)) || !Iterators.isEmpty(getOverriddenMethods(cls, method::isSameByJavaRules));
  }

  private boolean isVisibleInHierarchy(final JvmClass cls, final ProtoMember clsMember, final JvmClass subClass) {
    // optimized version, allows skipping isInheritor check
    return clsMember.isProtected() || isVisibleIn(cls, clsMember, subClass);
  }

  public boolean isVisibleIn(final JvmClass cls, final ProtoMember clsMember, final JvmClass scope) {
    if (clsMember.isPrivate()) {
      return Objects.equals(cls.getReferenceID(), scope.getReferenceID());
    }
    if (clsMember.isPackageLocal()) {
      return Objects.equals(cls.getPackageName(), scope.getPackageName());
    }
    if (clsMember.isProtected()) {
      return Objects.equals(cls.getPackageName(), scope.getPackageName()) || isInheritorOf(scope, cls);
    }
    return true;
  }

  public boolean isInheritorOf(JvmClass who, JvmClass whom) {
    return !Iterators.isEmpty(Iterators.filter(Iterators.recurseDepth(who, cl -> Iterators.flat(Iterators.map(who.getSuperTypes(), st -> getClassesByName(st))), true), cl -> cl.getReferenceID().equals(whom.getReferenceID())));
  }

  public boolean inheritsFromLibraryClass(JvmClass cls) {
    for (String st : cls.getSuperTypes()) {
      Iterator<JvmClass> classes = getClassesByName(st).iterator();
      if (!classes.hasNext()) {
        // the supertype is not present in the graph (not compiled yet?), assuming this is a library class
        return true;
      }
      while (classes.hasNext()) {
        if (inheritsFromLibraryClass(classes.next())) {
          return true;
        }
      }
    }
    return false;
  }

  public @Nullable Boolean isInheritorOf(JvmNodeReferenceID who, final JvmNodeReferenceID whom) {
    if (who.equals(whom) || !Iterators.isEmpty(Iterators.filter(allSupertypes(who), st -> st.equals(whom)))) {
      return Boolean.TRUE;
    }
    return null;
  }

  public Predicate<JvmMethod> lessSpecific(final JvmMethod than) {
    return m -> {
      if (m.isConstructor() || !Objects.equals(m.getName(), than.getName())) {
        return false;
      }
      Iterator<TypeRepr> it = m.getArgTypes().iterator();
      for (TypeRepr thanArgType : than.getArgTypes()) {
        if (!it.hasNext()) {
          return false;
        }
        TypeRepr mArgType = it.next();
        Boolean subtypeOf = isSubtypeOf(thanArgType, mArgType);
        if (subtypeOf != null && !subtypeOf) {
          return false;
        }
      }
      return !it.hasNext();
    };
  }

  @Nullable
  public Boolean isSubtypeOf(final TypeRepr who, final TypeRepr whom) {
    if (who.equals(whom)) {
      return Boolean.TRUE;
    }

    if (who instanceof TypeRepr.PrimitiveType || whom instanceof TypeRepr.PrimitiveType) {
      return Boolean.FALSE;
    }

    if (who instanceof TypeRepr.ArrayType) {
      if (whom instanceof TypeRepr.ArrayType) {
        return isSubtypeOf(((TypeRepr.ArrayType)who).getElementType(), ((TypeRepr.ArrayType)whom).getElementType());
      }

      final String descr = whom.getDescriptor();

      if (descr.equals("Ljava/lang/Cloneable") || descr.equals("Ljava/lang/Object") || descr.equals("Ljava/io/Serializable")) {
        return Boolean.TRUE;
      }

      return Boolean.FALSE;
    }

    if (whom instanceof TypeRepr.ClassType) {
      return isInheritorOf(new JvmNodeReferenceID(((TypeRepr.ClassType)who).getJvmName()), new JvmNodeReferenceID(((TypeRepr.ClassType)whom).getJvmName()));
    }

    return Boolean.FALSE;
  }


  private static <K, V> Function<K, V> cachingFunction(Function<K, V> f) {
    return new Function<>() {
      private final Map<K, V> cache = new HashMap<>();
      @Override
      public V apply(K k) {
        return cache.computeIfAbsent(k, f);
      }
    };
  }

  private void debug(String message) {
    // todo
  }
}
