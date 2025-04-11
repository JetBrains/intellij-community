// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.javac.Iterators;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * This class provides commonly used graph traversal methods.
 */
public final class Utils {
  private static final int JVM_CLASS_NODE_CACHE_SIZE = 1024;
  private final @NotNull Graph myGraph;
  private final @Nullable Delta myDelta;

  private final @NotNull Predicate<? super NodeSource> mySourcesFilter;
  private final @NotNull Predicate<? super ReferenceID> myIsNodeDeleted;
  private final @NotNull BackDependencyIndex myDirectSubclasses;
  private final @Nullable BackDependencyIndex myDeltaDirectSubclasses;

  private final Cache<ReferenceID, Iterable<JvmClass>> myJvmClassCache = Caffeine.newBuilder().maximumSize(JVM_CLASS_NODE_CACHE_SIZE).build();

  /**
   * Use this constructor for traversal during differentiate operation
   * @param context differentiate context
   * @param isDelta false, if new nodes in Delta should be taken into account, false otherwise
   */
  public Utils(@NotNull DifferentiateContext context, boolean isDelta) {
    this(context.getGraph(), isDelta? context.getDelta() : null, context.getParams().affectionFilter(), context::isDeleted);
  }

  /**
   * Use this constructor for ordinary graph traversal to explore currently stored dependencies
   * @param graph the graph to be explored
   * @param sourceFilter NodeSource filter limiting the scope of traversal. Usually this is used to limit the sources set to some scope defined by some external layout, (i.e. a module structure)
   */
  public Utils(@NotNull Graph graph, @NotNull Predicate<? super NodeSource> sourceFilter) {
    this(graph, null, sourceFilter, id -> false);
  }

  /**
   * The base constructor defining all necessary traversal parameters
   * @param graph the graph to be explored
   * @param delta the optional delta graph containing new nodes which are not yet integrated into the graph
   * @param sourceFilter NodeSource filter limiting the scope of traversal. Usually this is used to limit the sources set to some scope defined by some external layout, (i.e. a module structure)
   * @param isNodeDeleted predicate to test if some node currently existing in the graph will be deleted after changes from Delta are applied to the graph
   */
  public Utils(@NotNull Graph graph, @Nullable Delta delta, @NotNull Predicate<? super NodeSource> sourceFilter, @NotNull Predicate<? super ReferenceID> isNodeDeleted) {
    myGraph = graph;
    myDelta = delta;
    mySourcesFilter = sourceFilter;
    myIsNodeDeleted = isNodeDeleted;
    myDirectSubclasses = Objects.requireNonNull(myGraph.getIndex(SubclassesIndex.NAME));
    myDeltaDirectSubclasses = myDelta != null? Objects.requireNonNull(myDelta.getIndex(SubclassesIndex.NAME)) : null;
  }

  public Iterable<NodeSource> getNodeSources(ReferenceID nodeId) {
    if (myDelta != null) {
      Iterable<NodeSource> _src = myDelta.getSources(nodeId);
      Iterable<NodeSource> deltaSources = _src instanceof Set? _src : collect(_src, new HashSet<>()) /*ensure Set data structure*/;
      Set<NodeSource> deleted = myDelta.getDeletedSources();
      return flat(deltaSources, filter(myGraph.getSources(nodeId), src -> !contains(deltaSources, src) && !deleted.contains(src) && mySourcesFilter.test(src)));
    }
    return filter(myGraph.getSources(nodeId), mySourcesFilter::test);
  }

  public Iterable<JvmClass> getClassesByName(@NotNull String name) {
    return name.isBlank()? Collections.emptyList() : getJvmClassNodes(name);
  }

  public Iterable<JvmModule> getModulesByName(@NotNull String name) {
    return name.isBlank()? Collections.emptyList() : getNodes(new JvmNodeReferenceID(name), JvmModule.class);
  }

  public @Nullable String getNodeName(ReferenceID id) {
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
    return find(getJvmClassNodes(classId), this::isLambdaTarget) != null;
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

  private Iterable<JvmClass> getJvmClassNodes(@NotNull String name) {
    return getJvmClassNodes(new JvmNodeReferenceID(name));
  }
  
  private Iterable<JvmClass> getJvmClassNodes(@NotNull ReferenceID nodeId) {
    return myJvmClassCache.get(nodeId, id -> collect(getNodesImpl(id, JvmClass.class, false), new SmartList<>()));
  }

  /**
   * @param id a node reference ID
   * @return all nodes with the given ReferenceID. Nodes in the returned collection will have the same ReferenceID, but may be associated with different sources
   */
  public <T extends Node<T, ?>> Iterable<T> getNodes(@NotNull ReferenceID id, Class<T> selector) {
    return getNodesImpl(id, selector, false);
  }

  /**
   * @param id a node reference ID
   * @return all nodes with the given ReferenceID that have been compiled in the current compilation session (defined by the DifferentiateContext).
   */
  public <T extends Node<T, ?>> Iterable<T> getCompiledNodes(@NotNull ReferenceID id, Class<T> selector) {
    return getNodesImpl(id, selector, true);
  }

  private <T extends Node<T, ?>> @NotNull Iterable<T> getNodesImpl(@NotNull ReferenceID id, Class<T> selector, boolean fromDeltaOnly) {
    if (id instanceof JvmNodeReferenceID && "".equals(((JvmNodeReferenceID)id).getNodeName())) {
      return Collections.emptyList();
    }
    Iterable<T> allNodes;
    if (myDelta != null) {
      Iterable<NodeSource> deltaSrc = myDelta.getSources(id);
      Iterable<NodeSource> deltaSources = fromDeltaOnly? deltaSrc : deltaSrc instanceof Set? deltaSrc : collect(deltaSrc, new HashSet<>()) /*ensure Set data structure*/;
      Iterable<T> deltaNodes = flat(map(deltaSources, src -> myDelta.getNodes(src, selector)));
      if (fromDeltaOnly) {
        allNodes = deltaNodes;
      }
      else {
        Set<NodeSource> deleted = myDelta.getDeletedSources();
        allNodes = flat(
          deltaNodes, flat(map(filter(myGraph.getSources(id), src -> !contains(deltaSources, src) && !deleted.contains(src) && mySourcesFilter.test(src)), src -> myGraph.getNodes(src, selector)))
        );
      }
    }
    else {
      allNodes = fromDeltaOnly? Collections.emptyList() : flat(map(filter(myGraph.getSources(id), mySourcesFilter::test), src -> myGraph.getNodes(src, selector)));
    }
    return filter(allNodes, n -> id.equals(n.getReferenceID()));
  }

  public static <T> @NotNull Iterable<T> uniqueBy(Iterable<? extends T> it, final BiFunction<? super T, ? super T, Boolean> equalsImpl, final Function<? super T, Integer> hashCodeImpl) {
    return Iterators.uniqueBy(it, () -> new BooleanFunction<>() {
      Set<T> visited;

      @Override
      public boolean fun(T t) {
        if (visited == null) {
          visited = Containers.createCustomPolicySet(equalsImpl, hashCodeImpl);
        }
        return visited.add(t);
      }
    });
  }

  public Iterable<ReferenceID> allDirectSupertypes(ReferenceID classId) {
    return classId instanceof JvmNodeReferenceID? map(allDirectSupertypes((JvmNodeReferenceID)classId), id -> id) : Collections.emptyList();
  }

  public Iterable<JvmNodeReferenceID> allDirectSupertypes(JvmNodeReferenceID classId) {
    // return only those direct supertypes that exist in the graph as Nodes
    return unique(flat(map(getJvmClassNodes(classId), cl -> flat(map(cl.getSuperTypes(), st -> map(getJvmClassNodes(st), JvmClass::getReferenceID))))));
  }

  public Iterable<JvmClass> allDirectSupertypes(JvmClass cls) {
    // return only those direct supertypes that exist in the graph as Nodes
    return flat(map(cls.getSuperTypes(), this::getJvmClassNodes));
  }

  public Iterable<JvmNodeReferenceID> allSupertypes(JvmNodeReferenceID classId) {
    //return recurseDepth(className, this::allDirectSupertypes, false);
    return recurse(classId, this::allDirectSupertypes, false);
  }

  public @NotNull Iterable<ReferenceID> withAllSubclasses(ReferenceID from) {
    return recurse(from, this::directSubclasses, true);
  }

  public @NotNull Iterable<ReferenceID> allSubclasses(ReferenceID from) {
    return recurse(from, this::directSubclasses, false);
  }

  public @NotNull Iterable<ReferenceID> directSubclasses(ReferenceID from) {
    if (myDeltaDirectSubclasses != null) {
      BooleanFunction<ReferenceID> subClassFilter = sub -> {
        if (myIsNodeDeleted.test(sub)) {
          return false;
        }
        Iterable<JvmClass> justCompiled = getCompiledNodes(sub, JvmClass.class);
        // If the class has just been compiled and is stored in the delta, need to ensure the class is still a subclass of the given class
        return isEmpty(justCompiled) || contains(flat(map(justCompiled, cl -> map(cl.getSuperTypes(), st -> new JvmNodeReferenceID(st)))), from);
      };

      return unique(flat(filter(myDirectSubclasses.getDependencies(from), subClassFilter), myDeltaDirectSubclasses.getDependencies(from)));
    }
    return myDirectSubclasses.getDependencies(from);
  }

  public Set<JvmNodeReferenceID> collectSubclassesWithoutField(JvmNodeReferenceID classId, JvmField field) {
    return collectSubclassesWithoutMember(classId, f -> Objects.equals(field.getName(), f.getName()), JvmClass::getFields);
  }

  public Set<JvmNodeReferenceID> collectSubclassesWithoutMethod(JvmNodeReferenceID classId, JvmMethod method) {
    return collectSubclassesWithoutMember(classId, method::isSame, JvmClass::getMethods);
  }

  // propagateMemberAccess
  private <T extends ProtoMember> Set<JvmNodeReferenceID> collectSubclassesWithoutMember(JvmNodeReferenceID classId, Predicate<? super T> isSame, Function<JvmClass, Iterable<T>> membersGetter) {
    Predicate<ReferenceID> containsMember = id -> find(getJvmClassNodes(id), cls -> find(membersGetter.apply(cls), isSame::test) == null) == null;
    //stop further traversal, if nodes corresponding to the subclassName contain matching member
    Iterable<JvmNodeReferenceID> result = getNodesData(
      classId,
      this::directSubclasses,
      id -> id instanceof JvmNodeReferenceID && !containsMember.test(id)? (JvmNodeReferenceID)id : null,
      Objects::nonNull,
      false
    );
    return collect(filter(result, notNullFilter()), new HashSet<>());
  }

  public Iterable<Pair<JvmClass, JvmField>> getOverriddenFields(JvmClass fromCls, JvmField field) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmField>>> dataGetter = cls -> collect(
      map(filter(cls.getFields(), f -> Objects.equals(f.getName(), field.getName()) && isVisibleInHierarchy(cls, f, fromCls)), ff -> Pair.create(cls, ff)),
      new SmartList<>()
    );
    return flat(
      getNodesData(fromCls, cl -> flat(map(cl.getSuperTypes(), this::getClassesByName)), dataGetter, Iterators::isEmpty, false)
    );
  }

  public Iterable<Pair<JvmClass, JvmMethod>> getOverriddenMethods(JvmClass fromCls, Predicate<JvmMethod> searchCond) {
    Function<JvmClass, Iterable<Pair<JvmClass, JvmMethod>>> dataGetter = cls -> collect(
      map(filter(cls.getMethods(), m -> searchCond.test(m) && isVisibleInHierarchy(cls, m, fromCls)), mm -> Pair.create(cls, mm)),
      new SmartList<>()
    );
    return flat(
      getNodesData(fromCls, cl -> flat(map(cl.getSuperTypes(), this::getClassesByName)), dataGetter, Iterators::isEmpty, false)
    );
  }

  public Iterable<Pair<JvmClass, JvmMethod>> getOverridingMethods(JvmClass fromCls, JvmMethod method, Predicate<JvmMethod> searchCond) {
    Function<JvmClass, Iterable<JvmMethod>> matchingMethodsGetter = cachingFunction(
      cl -> fromCls.isSame(cl)?
            List.of() :
            collect(filter(cl.getMethods(), searchCond::test), new SmartList<>())
    );
    
    Function<JvmClass, Iterable<Pair<JvmClass, JvmMethod>>> dataGetter = cls -> isVisibleInHierarchy(fromCls, method, cls)? collect(
      flat(map(withAllImplementedInterfaces(cls), c -> map(matchingMethodsGetter.apply(c), mm -> Pair.create(cls, mm)))),
      new SmartList<>()
    ) : Collections.emptyList();
    
    return flat(
      getNodesData(fromCls, cl -> flat(map(directSubclasses(cl.getReferenceID()), this::getJvmClassNodes)), dataGetter, Iterators::isEmpty, false)
    );
  }

  private Iterable<JvmClass> withAllImplementedInterfaces(JvmClass cls) {
    return recurse(cls, cl -> flat(map(cl.getInterfaces(), this::getClassesByName)), true);
  }

  public static final class OverloadDescriptor {
    final JVMFlags accessScope;
    final JvmMethod overloadMethod;
    final JvmClass owner;

    OverloadDescriptor(JVMFlags accessScope, JvmMethod overloadMethod, JvmClass owner) {
      this.accessScope = accessScope;
      this.overloadMethod = overloadMethod;
      this.owner = owner;
    }
  }

  public Iterable<OverloadDescriptor> findAllOverloads(final JvmClass cls, Function<? super JvmMethod, JVMFlags> correspondenceFinder) {
    Function<JvmClass, Iterable<OverloadDescriptor>> mapper = c -> filter(map(c.getMethods(), m -> {
      JVMFlags accessScope = correspondenceFinder.apply(m);
      return accessScope != null? new OverloadDescriptor(accessScope, m, c) : null;
    }), notNullFilter());

    return flat(
      flat(map(recurse(cls, cl -> flat(map(cl.getSuperTypes(), this::getClassesByName)), true), mapper::apply)),
      flat(map(allSubclasses(cls.getReferenceID()), id -> flat(map(getJvmClassNodes(id), mapper::apply))))
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
    return find(cls.getFields(), field::isSame) != null || !isEmpty(getOverriddenFields(cls, field));
  }
  
  boolean isMethodVisible(final JvmClass cls, final JvmMethod method) {
    return find(cls.getMethods(), method::isSameByJavaRules) != null || !isEmpty(getOverriddenMethods(cls, method::isSameByJavaRules));
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
      return Objects.equals(cls.getPackageName(), scope.getPackageName()) || isSameOrInheritorOf(scope, cls);
    }
    return true;
  }

  public boolean isSameOrInheritorOf(JvmClass who, JvmClass whom) {
    return find(recurseDepth(who, cl -> flat(map(who.getSuperTypes(), this::getClassesByName)), true), cl -> cl.getReferenceID().equals(whom.getReferenceID())) != null;
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
    if (whom.equals(who) || find(recurseDepth(who, this::allDirectSupertypes, false), whom::equals) != null) {
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

  public @Nullable Boolean isSubtypeOf(final TypeRepr who, final TypeRepr whom) {
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
    Map<K, V> cache = new HashMap<>();
    return k -> cache.computeIfAbsent(k, f);
  }

  public static <V> Supplier<V> lazyValue(Supplier<V> provider) {
    return new Supplier<>() {
      private Object[] computed;

      @Override
      public V get() {
        //noinspection unchecked
        return computed == null? (V)(computed = new Object[] {provider.get()})[0] : (V)computed[0];
      }
    };
  }

}
