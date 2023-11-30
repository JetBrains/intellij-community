/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CollectionUtils {
  private static final CallMatcher COLLECTION_MAP_SIZE =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "size").parameterCount(0),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "size").parameterCount(0));

  /**
   * Matches a call which creates collection of the same size as the qualifier collection
   */
  public static final CallMatcher DERIVED_COLLECTION =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "keySet", "values", "entrySet").parameterCount(0),
      CallMatcher.instanceCall("java.util.NavigableMap", "descendingKeySet", "descendingMap", "navigableKeySet").parameterCount(0),
      CallMatcher.instanceCall("java.util.NavigableSet", "descendingSet").parameterCount(0)
    );

  /**
   */
  @NonNls private static final Set<String> s_allCollectionClassesAndInterfaces;
  /**
   */
  @NonNls private static final Map<String, String> s_interfaceForCollection =
    new HashMap<>();

  static {
    s_allCollectionClassesAndInterfaces = Set.of(
    "java.util.AbstractCollection",
    "java.util.AbstractList",
    "java.util.AbstractMap",
    "java.util.AbstractQueue",
    "java.util.AbstractSequentialList",
    "java.util.AbstractSet",
    "java.util.ArrayList",
    "java.util.ArrayDeque",
    CommonClassNames.JAVA_UTIL_COLLECTION,
    CommonClassNames.JAVA_UTIL_DICTIONARY,
    "java.util.EnumMap",
    CommonClassNames.JAVA_UTIL_HASH_MAP,
    CommonClassNames.JAVA_UTIL_HASH_SET,
    "java.util.Hashtable",
    "java.util.IdentityHashMap",
    "java.util.LinkedHashMap",
    CommonClassNames.JAVA_UTIL_LINKED_HASH_SET,
    CommonClassNames.JAVA_UTIL_LINKED_LIST,
    CommonClassNames.JAVA_UTIL_LIST,
    CommonClassNames.JAVA_UTIL_MAP,
    "java.util.PriorityQueue",
    CommonClassNames.JAVA_UTIL_QUEUE,
    CommonClassNames.JAVA_UTIL_SET,
    "java.util.SortedMap",
    CommonClassNames.JAVA_UTIL_SORTED_SET,
    CommonClassNames.JAVA_UTIL_STACK,
    "java.util.TreeMap",
    "java.util.TreeSet",
    "java.util.Vector",
    "java.util.WeakHashMap",
    "java.util.concurrent.ArrayBlockingQueue",
    "java.util.concurrent.BlockingDeque",
    "java.util.concurrent.BlockingQueue",
    "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.ConcurrentLinkedDeque",
    "java.util.concurrent.ConcurrentLinkedQueue",
    "java.util.concurrent.ConcurrentMap",
    "java.util.concurrent.ConcurrentNavigableMap",
    "java.util.concurrent.ConcurrentSkipListMap",
    "java.util.concurrent.ConcurrentSkipListSet",
    "java.util.concurrent.CopyOnWriteArrayList",
    "java.util.concurrent.CopyOnWriteArraySet",
    "java.util.concurrent.DelayQueue",
    "java.util.concurrent.LinkedBlockingDeque",
    "java.util.concurrent.LinkedBlockingQueue",
    "java.util.concurrent.LinkedTransferQueue",
    "java.util.concurrent.PriorityBlockingQueue",
    "java.util.concurrent.SynchronousQueue",
    "com.sun.java.util.collections.ArrayList",
    "com.sun.java.util.collections.Collection",
    "com.sun.java.util.collections.HashMap",
    "com.sun.java.util.collections.HashSet",
    "com.sun.java.util.collections.Hashtable",
    "com.sun.java.util.collections.LinkedList",
    "com.sun.java.util.collections.List",
    "com.sun.java.util.collections.Map",
    "com.sun.java.util.collections.Set",
    "com.sun.java.util.collections.SortedMap",
    "com.sun.java.util.collections.SortedSet",
    "com.sun.java.util.collections.TreeMap",
    "com.sun.java.util.collections.TreeSet",
    "com.sun.java.util.collections.Vector");

    s_interfaceForCollection.put("ArrayList", "List");
    s_interfaceForCollection.put("EnumMap", "Map");
    s_interfaceForCollection.put("EnumSet", "Set");
    s_interfaceForCollection.put("HashMap", "Map");
    s_interfaceForCollection.put("HashSet", "Set");
    s_interfaceForCollection.put("Hashtable", "Map");
    s_interfaceForCollection.put("IdentityHashMap", "Map");
    s_interfaceForCollection.put("LinkedHashMap", "Map");
    s_interfaceForCollection.put("LinkedHashSet", "Set");
    s_interfaceForCollection.put("LinkedList", "List");
    s_interfaceForCollection.put("PriorityQueue", "Queue");
    s_interfaceForCollection.put("TreeMap", "Map");
    s_interfaceForCollection.put("TreeSet", "SortedSet");
    s_interfaceForCollection.put("Vector", "List");
    s_interfaceForCollection.put("WeakHashMap", "Map");
    s_interfaceForCollection.put("java.util.ArrayList", CommonClassNames.JAVA_UTIL_LIST);
    s_interfaceForCollection.put("java.util.EnumMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.EnumSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.HashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.HashSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.Hashtable", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.IdentityHashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.LinkedHashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put(CommonClassNames.JAVA_UTIL_LINKED_HASH_SET, CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put(CommonClassNames.JAVA_UTIL_LINKED_LIST, CommonClassNames.JAVA_UTIL_LIST);
    s_interfaceForCollection.put("java.util.PriorityQueue", CommonClassNames.JAVA_UTIL_QUEUE);
    s_interfaceForCollection.put("java.util.TreeMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.TreeSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.Vector", CommonClassNames.JAVA_UTIL_LIST);
    s_interfaceForCollection.put("java.util.WeakHashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("com.sun.java.util.collections.HashSet", "com.sun.java.util.collections.Set");
    s_interfaceForCollection.put("com.sun.java.util.collections.TreeSet", "com.sun.java.util.collections.Set");
    s_interfaceForCollection.put("com.sun.java.util.collections.Vector", "com.sun.java.util.collections.List");
    s_interfaceForCollection.put("com.sun.java.util.collections.ArrayList", "com.sun.java.util.collections.List");
    s_interfaceForCollection.put("com.sun.java.util.collections.LinkedList", "com.sun.java.util.collections.List");
    s_interfaceForCollection.put("com.sun.java.util.collections.TreeMap", "com.sun.java.util.collections.Map");
    s_interfaceForCollection.put("com.sun.java.util.collections.HashMap", "com.sun.java.util.collections.Map");
    s_interfaceForCollection.put("com.sun.java.util.collections.Hashtable", "com.sun.java.util.collections.Map");
  }

  private CollectionUtils() {
    super();
  }

  public static Set<String> getAllCollectionNames() {
    return s_allCollectionClassesAndInterfaces;
  }

  @Contract("null -> false")
  public static boolean isConcreteCollectionClass(@Nullable PsiType type) {
    return isConcreteCollectionClass(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  @Contract("null -> false")
  public static boolean isConcreteCollectionClass(PsiClass aClass) {
    if (aClass == null || aClass.isEnum() || aClass.isInterface() || aClass.isAnnotationType() ||
        aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) &&
        !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
      return false;
    }
    @NonNls final String name = aClass.getQualifiedName();
    return name != null && name.startsWith("java.util.");
  }

  public static boolean isCollectionClassOrInterface(@Nullable PsiType type) {
    final PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(type);
    if (resolved == null) {
      return false;
    }
    return InheritanceUtil.isInheritor(resolved, CommonClassNames.JAVA_UTIL_COLLECTION) ||
           InheritanceUtil.isInheritor(resolved, CommonClassNames.JAVA_UTIL_MAP) ||
           InheritanceUtil.isInheritor(resolved, "com.google.common.collect.Multimap") ||
           InheritanceUtil.isInheritor(resolved, "com.google.common.collect.Table");
  }

  public static boolean isCollectionClassOrInterface(PsiClass aClass) {
    return isCollectionClassOrInterface(aClass, new HashSet<>());
  }

  /**
   * alreadyChecked set to avoid infinite loop in constructs like:
   * class C extends C {}
   */
  private static boolean isCollectionClassOrInterface(
    PsiClass aClass, Set<? super PsiClass> visitedClasses) {
    if (!visitedClasses.add(aClass)) {
      return false;
    }
    final String className = aClass.getQualifiedName();
    if (s_allCollectionClassesAndInterfaces.contains(className)) {
      return true;
    }
    final PsiClass[] supers = aClass.getSupers();
    for (PsiClass aSuper : supers) {
      if (isCollectionClassOrInterface(aSuper, visitedClasses)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isWeakCollectionClass(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final String typeText = type.getCanonicalText();
    return "java.util.WeakHashMap".equals(typeText);
  }

  public static boolean isConstantEmptyArray(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC) ||
        !field.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    return isEmptyArray(field);
  }

  public static boolean isEmptyArray(PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer instanceof PsiArrayInitializerExpression arrayInitializerExpression) {
      final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
      return initializers.length == 0;
    }
    return ConstructionUtils.isEmptyArrayInitializer(initializer);
  }

  public static String getInterfaceForClass(String name) {
    final int parameterStart = name.indexOf('<');
    final String baseName;
    if (parameterStart >= 0) {
      baseName = name.substring(0, parameterStart).trim();
    }
    else {
      baseName = name;
    }
    return s_interfaceForCollection.get(baseName);
  }

  /**
   * Checks whether supplied expression represents a collection size. It handles some derived collections,
   * e.g. it's known that {@code map.size()} is the size of {@code map.keySet()}.
   *
   * @param expression expression to test
   * @param collection expected collection or map expression
   * @return true if the supplied expression represents a collection size
   */
  @Contract("null, _ -> false")
  public static boolean isCollectionOrMapSize(@Nullable PsiExpression expression, @NotNull PsiExpression collection) {
    PsiMethodCallExpression sizeCall = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
    if (!COLLECTION_MAP_SIZE.test(sizeCall)) return false;
    PsiExpression sizeQualifier = sizeCall.getMethodExpression().getQualifierExpression();
    if (sizeQualifier == null) return false;
    sizeQualifier = getBaseCollection(sizeQualifier);
    collection = getBaseCollection(collection);
    return sizeQualifier != null && collection != null && PsiEquivalenceUtil.areElementsEquivalent(sizeQualifier, collection);
  }

  private static @Nullable PsiExpression getBaseCollection(@NotNull PsiExpression derivedCollection) {
    while(true) {
      PsiMethodCallExpression derivedCall =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(derivedCollection), PsiMethodCallExpression.class);
      if (DERIVED_COLLECTION.test(derivedCall)) {
        derivedCollection = ExpressionUtils.getEffectiveQualifier(derivedCall.getMethodExpression());
      }
      else {
        return derivedCollection;
      }
    }
  }
}