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
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.javac.Iterators;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

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
    return !isEmpty(sources)? sources : myGraph.getSources(nodeId);
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
    //noinspection unchecked
    Iterable<JVMClassNode> nodes = getNodes(id, JVMClassNode.class);
    for (var node : nodes) {
      return node.getName();
    }
    return null;
  }

  // test if a JvmClass is a SAM interface
  public boolean isLambdaTarget(ReferenceID classId) {
    return !isEmpty(filter(getNodes(classId, JvmClass.class), this::isLambdaTarget));
  }

  public boolean isLambdaTarget(JvmClass cls) {
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
    return false;
  }

  // return all methods of this class including all inherited methods recursively
  public Iterable<JvmMethod> allMethodsRecursively(JvmClass cls) {
    return flat(
      map(recurse(cls, c -> flat(map(c.getSuperTypes(), st -> getClassesByName(st))), true), c -> c.getMethods())
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
      Set<NodeSource> deltaSources = collect(myDelta.getSources(id), new HashSet<>());
      allNodes = flat(
        flat(map(deltaSources, src -> myDelta.getNodes(src, selector))), flat(map(filter(myGraph.getSources(id), src -> !deltaSources.contains(src)), src -> myGraph.getNodes(src, selector)))
      );
    }
    else {
      allNodes = flat(map(myGraph.getSources(id), src -> myGraph.getNodes(src, selector)));
    }
    return uniqueBy(filter(allNodes, n -> id.equals(n.getReferenceID())), () -> new Iterators.BooleanFunction<>() {
      Set<T> visited;
      @Override
      public boolean fun(T t) {
        if (visited == null) {
          visited = Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode);
        }
        return visited.add(t);
      }
    });
  }

  public Iterable<JvmNodeReferenceID> allDirectSupertypes(JvmNodeReferenceID classId) {
    return unique(flat(map(getNodes(classId, JvmClass.class), cl -> map(cl.getSuperTypes(), st -> new JvmNodeReferenceID(st)))));
  }

  public Iterable<JvmNodeReferenceID> allSupertypes(JvmNodeReferenceID classId) {
    //return Iterators.recurseDepth(className, s -> allDirectSupertypes(s), false);
    return recurse(classId, s -> allDirectSupertypes(s), false);
  }

  @NotNull
  public Iterable<ReferenceID> withAllSubclasses(ReferenceID from) {
    return recurse(from, myDirectSubclasses::getDependencies, true);
  }

  @NotNull
  public Iterable<ReferenceID> allSubclasses(ReferenceID from) {
    return recurse(from, myDirectSubclasses::getDependencies, false);
  }

  public Set<JvmNodeReferenceID> collectSubclassesWithoutField(JvmNodeReferenceID classId, JvmField field) {
    return collectSubclassesWithoutMember(classId, f -> Objects.equals(field.getName(), f.getName()), JvmClass::getFields);
  }

  public Set<JvmNodeReferenceID> collectSubclassesWithoutMethod(JvmNodeReferenceID classId, JvmMethod method) {
    return collectSubclassesWithoutMember(classId, method::isSame, JvmClass::getMethods);
  }

  // propagateMemberAccess
  private <T extends ProtoMember> Set<JvmNodeReferenceID> collectSubclassesWithoutMember(JvmNodeReferenceID classId, Predicate<? super T> isSame, Function<JvmClass, Iterable<T>> membersGetter) {
    Predicate<ReferenceID> containsMember = id -> isEmpty(filter(getNodes(id, JvmClass.class), cls -> isEmpty(filter(membersGetter.apply(cls), isSame::test))));
    //stop further traversal, if nodes corresponding to the subclassName contain matching member
    Iterable<JvmNodeReferenceID> result = getNodesData(
      (ReferenceID)classId,
      id -> myDirectSubclasses.getDependencies(id),
      id -> id instanceof JvmNodeReferenceID && !containsMember.test(id)? (JvmNodeReferenceID)id : null,
      Objects::nonNull,
      false
    );
    return collect(filter(result, notNullFilter()), new HashSet<>());
  }

  public Iterable<Pair<JvmClass, JvmField>> getOverriddenFields(JvmClass fromCls, JvmField field) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmField>>> dataGetter = cl -> collect(
      map(filter(cl.getFields(), f -> Objects.equals(f.getName(), field.getName()) && isVisibleInHierarchy(cl, f, fromCls)), ff -> Pair.create(cl, ff)),
      new SmartList<>()
    );
    return flat(
      getNodesData(fromCls, cl -> flat(map(cl.getSuperTypes(), st -> getClassesByName(st))), dataGetter, result -> isEmpty(result), false)
    );
  }

  public Iterable<Pair<JvmClass, JvmMethod>> getOverriddenMethods(JvmClass fromCls, Predicate<JvmMethod> searchCond) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmMethod>>> dataGetter = cl -> collect(
      map(filter(cl.getMethods(), m -> searchCond.test(m) && isVisibleInHierarchy(cl, m, fromCls)), mm -> Pair.create(cl, mm)),
      new SmartList<>()
    );
    return flat(
      getNodesData(fromCls, cl -> flat(map(cl.getSuperTypes(), st -> getClassesByName(st))), dataGetter, result -> isEmpty(result), false)
    );
  }

  public Iterable<Pair<JvmClass, JvmMethod>> getOverridingMethods(JvmClass fromCls, JvmMethod method, Predicate<JvmMethod> searchCond) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmMethod>>> dataGetter = cl -> isVisibleInHierarchy(fromCls, method, cl)? collect(
      map(filter(cl.getMethods(), searchCond::test), mm -> Pair.create(cl, mm)),
      new SmartList<>()
    ) : Collections.emptyList();
    return flat(
      getNodesData(fromCls, cl -> flat(map(myDirectSubclasses.getDependencies(cl.getReferenceID()), st -> getNodes(st, JvmClass.class))), dataGetter, result -> isEmpty(result), false)
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
    return map(recurseDepth(fromNode, node -> fromNode.equals(node) || continuationCond.test(mapper.apply(node))? step.apply(node): Collections.emptyList(), includeHead), mapper::apply);
  }

  public boolean hasOverriddenMethods(JvmClass cls, JvmMethod method) {
    return !isEmpty(getOverriddenMethods(cls, method::isSameByJavaRules)) || inheritsFromLibraryClass(cls) /*assume the method can override some method from the library*/;
  }

  boolean isFieldVisible(final JvmClass cls, final JvmField field) {
    return !isEmpty(filter(cls.getFields(), field::isSame)) || !isEmpty(getOverriddenFields(cls, field));
  }
  
  boolean isMethodVisible(final JvmClass cls, final JvmMethod method) {
    return !isEmpty(filter(cls.getMethods(), method::isSameByJavaRules)) || !isEmpty(getOverriddenMethods(cls, method::isSameByJavaRules));
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
    return !isEmpty(filter(recurseDepth(who, cl -> flat(map(who.getSuperTypes(), st -> getClassesByName(st))), true), cl -> cl.getReferenceID().equals(whom.getReferenceID())));
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
    if (who.equals(whom) || !isEmpty(filter(allSupertypes(who), st -> st.equals(whom)))) {
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

      if ("Ljava/lang/Cloneable;".equals(descr) || "Ljava/lang/Object;".equals(descr) || "Ljava/io/Serializable;".equals(descr)) {
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

  public static <V> Iterators.Provider<V> lazyValue(Iterators.Provider<V> provider) {
    return new Iterators.Provider<>() {
      private Object[] computed;

      @Override
      public V get() {
        //noinspection unchecked
        return computed == null? (V)(computed = new Object[] {provider.get()})[0] : (V)computed[0];
      }
    };
  }
}
