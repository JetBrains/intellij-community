// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * This class checks the contract described in {@link com.intellij.psi.util.CachedValue} documentation, that its
 * {@link CachedValueProvider} may not capture anything short-lived, thread-local or caller-dependent from the context,
 * to avoid unstable and invalid cached results. The checker uses reflection to traverse fields of the given provider
 * to ensure they contain the same (or equal) values when invoked multiple times.
 * If a non-equivalent captured value is detected, it's reported and should be fixed.<p></p>
 *
 * The fix usually involves getting rid of the problematic dependency. This often can be done by moving some sub-expressions
 * in or out of the cached value provider, or extracting the whole cached value access into a static method (to avoid capturing "this").
 * In rare cases, if the values are equivalent and "safe" (don't leak anything short-lived and don't contain different data depending on calling context),
 * it makes sense to define {@link Object#equals(Object)} in their class.<p></p>
 *
 * Occasionally, false positives may be reported:
 * <ol>
 *   <li>Providers might capture their containing "this" instance, but use only some "safe" fields
 *    from it (e.g. project). This is still a dangerous case, because it's very easy to accidentally access other fields
 *    from the same class later, which can be more transient and caller-dependent.
 *    Extracting cached value access into a static method should prevent that and get rid of the error.</li>
 *  <li>
 *    The provider itself might contain some mutable fields, e.g. lazily initialized. They might be harmless, but might also
 *    depend on the world in the moment when they're first accessed, and be used later when the provider is re-run in a changed world.
 *    This case is usually easy to work around by
 *    converting those fields to local variables, passing them as parameters, or extracting another class with those fields
 *    and instantiating it on each CachedValueProvider invocation.
 *  </li>
 *  <li>
 *    If there are cyclic dependencies in the object graph of the cached value provider (e.g. it delegates to a Function
 *    which somehow refers back to the provider), the checker will complain. Such cycles should be avoided.
 *  </li>
 * </ol>
 *
 * Note that these checks only happen in {@link com.intellij.psi.util.CachedValuesManager#getCachedValue} methods,
 * where providers are instantiated afresh on each call. If you manually create a cached value and put into a field or
 * user data, you're still prone to the very same issues with capturing unstable values from the context,
 * but no checks can catch that for you, you're on your own.
 */
final class CachedValueStabilityChecker {
  private static final Logger LOG = Logger.getInstance(CachedValueStabilityChecker.class);
  private static final Set<String> ourReportedKeys = ContainerUtil.newConcurrentSet();
  private static final ConcurrentMap<Class<?>, List<Field>> ourFieldCache = ConcurrentFactoryMap.createMap(ReflectionUtil::collectFields);
  private static final boolean DO_CHECKS = shouldDoChecks();

  private static boolean shouldDoChecks() {
    Application app = ApplicationManager.getApplication();
    return app.isUnitTestMode() || app.isInternal() || app.isEAP();
  }

  static void checkProvidersEquivalent(CachedValueProvider<?> p1, CachedValueProvider<?> p2, Key<?> key) {
    if (p1 == p2 || !DO_CHECKS || ApplicationInfoImpl.isInStressTest()) return;

    if (p1.getClass() != p2.getClass()) {
      if (!seemConcurrentlyCreatedLambdas(p1.getClass(), p2.getClass())) {
        complain("Incorrect CachedValue use: different providers supplied for the same key: " + p1 + " and " + p2, key.toString(), p1.getClass());
      }
      return;
    }

    checkFieldEquivalence(p1, p2, key.toString(), 0, p1.getClass());
  }

  /**
   * Several classes can be generated concurrently at runtime for the same lambda,
   * we can neither avoid that nor check equivalence in this case.
   */
  private static boolean seemConcurrentlyCreatedLambdas(Class<?> c1, Class<?> c2) {
    if (c1 == c2) return false;

    String name1 = c1.getName();
    String name2 = c2.getName();
    int index = name1.indexOf("$$Lambda");
    return index > 0 &&
           index == name2.indexOf("$$Lambda") &&
           name2.startsWith(name1.substring(0, index)) &&
           ourFieldCache.get(c1).size() == ourFieldCache.get(c2).size();
  }

  private static boolean checkFieldEquivalence(Object o1, Object o2, String key, int depth, @NotNull Class<?> pluginClass) {
    if (depth > 100) {
      complain("Too deep function delegation inside CachedValueProvider. If you have cyclic dependencies, please remove them.", key, pluginClass);
      return false;
    }

    for (Field field : ourFieldCache.get(o1.getClass())) {
      Object v1;
      Object v2;
      try {
        field.setAccessible(true);
        v1 = field.get(o1);
        v2 = field.get(o2);
      }
      catch (Exception e) {
        throw new UnsupportedOperationException("Please allow full reflective access");
      }

      if (areEqual(v1, v2)) continue;

      if (v1 != null && v2 != null && seemConcurrentlyCreatedLambdas(v1.getClass(), v2.getClass())) {
        continue;
      }

      if (v1 != null && v2 != null && v1.getClass() == v2.getClass() && shouldGoDeeper(v1)) {
        if (!checkFieldEquivalence(v1, v2, key, depth + 1, v1.getClass())) {
          return false;
        }
      } else {
        complain(nonEquivalence(o1.getClass(), field, v1, v2), key, pluginClass);
        return false;
      }
    }
    return true;
  }

  private static boolean areEqual(Object v1, Object v2) {
    if (Objects.equals(v1, v2)) return true;

    if (v1 instanceof Object[] && v2 instanceof Object[]) {
      return Arrays.deepEquals((Object[])v1, (Object[])v2);
    }

    if (v1 instanceof Reference && v2 instanceof Reference) {
      return Objects.equals(((Reference<?>)v1).get(), ((Reference<?>)v2).get());
    }

    return false;
  }

  @NotNull
  private static String nonEquivalence(Class<?> objectClass, Field field, @Nullable Object v1, @Nullable Object v2) {
    return "Incorrect CachedValue use: same CV with different captured context, this can cause unstable results and invalid PSI access." +
           "\nField " + field.getName() + " in " + objectClass + " has non-equivalent values:" +
           "\n  " + v1 + (v1 == null ? "" : " (" + v1.getClass().getName() + ")") + " and" +
           "\n  " + v2 + (v2 == null ? "" : " (" + v2.getClass().getName() + ")") +
           "\nEither make `equals()` hold for these values, or avoid this dependency, e.g. by extracting CV provider into a static method.";
  }

  private static void complain(String message, String key, @NotNull Class<?> pluginClass) {
    if (ourReportedKeys.add(key)) {
      // curious why you've gotten this error? Maybe this class' javadoc will help.
      PluginException.logPluginError(LOG, message, null, pluginClass);
    }
  }

  private static boolean shouldGoDeeper(Object o) {
    if (o instanceof CachedValueProvider) return true;

    Class<?> clazz = o.getClass();
    Class<?> superclass = clazz.getSuperclass();
    if (superclass == null) return false;

    if ((o instanceof Supplier || o instanceof Function || o instanceof java.util.function.Function) &&
        Object.class.equals(clazz.getSuperclass())) {
      return true;
    }

    return "kotlin.jvm.internal.Lambda".equals(superclass.getName());
  }

  static void cleanupFieldCache() {
    ourFieldCache.clear();
  }
}
