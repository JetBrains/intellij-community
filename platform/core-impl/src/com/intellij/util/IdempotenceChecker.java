// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.model.Symbol;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveResult;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class IdempotenceChecker {
  private static final Logger LOG = Logger.getInstance(IdempotenceChecker.class);
  private static final Set<Class<?>> ourReportedValueClasses = Collections.synchronizedSet(new HashSet<>());
  private static final ThreadLocal<AtomicInteger> ourRandomCheckNesting = ThreadLocal.withInitial(() -> new AtomicInteger());
  @SuppressWarnings("SSBasedInspection") private static final ThreadLocal<List<String>> ourLog = new ThreadLocal<>();

  private static final Supplier<RegistryValue> rateCheckProperty = new SynchronizedClearableLazy<>(() -> {
    return Registry.get("platform.random.idempotence.check.rate");
  });

  /**
   * Perform some basic checks whether the two given objects are equivalent and interchangeable,
   * as described in e.g {@link com.intellij.psi.util.CachedValue} contract. This method should be used
   * in places caching results of various computations, which are expected to be idempotent:
   * they can be performed several times, or on multiple threads, and the results should be interchangeable.<p></p>
   *
   * What to do if you get an error from here:
   * <ul>
   *   <li>
   *     Start by looking carefully at the computation (which usually can be found by navigating the stack trace)
   *     and find out why it could be non-idempotent. See common culprits below.</li>
   *   <li>
   *     Add logging inside the computation by using {@link #logTrace}.
   *   </li>
   *   <li>
   *     If the computation is complex and depends on other caches, you could try to perform
   *     {@code IdempotenceChecker.checkEquivalence()} for their results as well, localizing the error.</li>
   *   <li>
   *     If it's a test, you could try reproducing and debugging it. To increase the probability of failure,
   *     you can temporarily add {@code Registry.get("platform.random.idempotence.check.rate").setValue(1, getTestRootDisposable())}
   *     to perform the idempotence check on every cache access. Note that this can make your test much slower.
   *   </li>
   * </ul>
   *
   * Common culprits:
   * <ul>
   *   <li>Caching and returning a mutable object (e.g. array or List), which clients then mutate from different threads;
   *   to fix, either clone the return value or use unmodifiable wrappers</li>
   *   <li>Depending on a {@link ThreadLocal} or method parameters with different values.</li>
   *   <li>For failures from {@link #applyForRandomCheck}: outdated cached value (not all dependencies are specified, or their modification counters aren't properly incremented)</li>
   * </ul>
   *
   * @param existing the value computed on the first invocation
   * @param fresh the value computed a bit later, expected to be equivalent
   * @param providerClass a class of the function performing the computation, used to prevent reporting the same error multiple times
   * @param recomputeValue optionally, a way to recalculate the value one more time with {@link #isLoggingEnabled()} true,
   *                       and include the log collected via {@link #logTrace} into exception report.
   */
  public static <T> void checkEquivalence(@Nullable T existing,
                                          @Nullable T fresh,
                                          @NotNull Class<?> providerClass,
                                          @Nullable Computable<? extends T> recomputeValue) {
    checkEquivalence(existing, fresh, providerClass, recomputeValue, null);
  }

  /**
   * Perform some basic checks whether the two given objects are equivalent and interchangeable,
   * as described in e.g {@link com.intellij.psi.util.CachedValue} contract. This method should be used
   * in places caching results of various computations, which are expected to be idempotent:
   * they can be performed several times, or on multiple threads, and the results should be interchangeable.<p></p>
   * <p>
   * What to do if you get an error from here:
   * <ul>
   *   <li>
   *     Start by looking carefully at the computation (which usually can be found by navigating the stack trace)
   *     and find out why it could be non-idempotent. See common culprits below.</li>
   *   <li>
   *     Add logging inside the computation by using {@link #logTrace}.
   *   </li>
   *   <li>
   *     If the computation is complex and depends on other caches, you could try to perform
   *     {@code IdempotenceChecker.checkEquivalence()} for their results as well, localizing the error.</li>
   *   <li>
   *     If it's a test, you could try reproducing and debugging it. To increase the probability of failure,
   *     you can temporarily add {@code Registry.get("platform.random.idempotence.check.rate").setValue(1, getTestRootDisposable())}
   *     to perform the idempotence check on every cache access. Note that this can make your test much slower.
   *   </li>
   * </ul>
   * <p>
   * Common culprits:
   * <ul>
   *   <li>Caching and returning a mutable object (e.g. array or List), which clients then mutate from different threads;
   *   to fix, either clone the return value or use unmodifiable wrappers</li>
   *   <li>Depending on a {@link ThreadLocal} or method parameters with different values.</li>
   *   <li>For failures from {@link #applyForRandomCheck}: outdated cached value (not all dependencies are specified, or their modification counters aren't properly incremented)</li>
   * </ul>
   *  @param existing the value computed on the first invocation
   *
   * @param fresh               the value computed a bit later, expected to be equivalent
   * @param providerClass       a class of the function performing the computation, used to prevent reporting the same error multiple times
   * @param recomputeValue      optionally, a way to recalculate the value one more time with {@link #isLoggingEnabled()} true,
   *                            and include the log collected via {@link #logTrace} into exception report.
   * @param contextInfoSupplier a supplier that may provide additional contextual information to log
   */
  static <T> void checkEquivalence(@Nullable T existing,
                                   @Nullable T fresh,
                                   @NotNull Class<?> providerClass,
                                   @Nullable Computable<? extends T> recomputeValue,
                                   @Nullable Supplier<String> contextInfoSupplier) {
    String msg = checkValueEquivalence(existing, fresh);
    if (msg != null) {
      reportFailure(existing, fresh, providerClass, recomputeValue,
                    msg + (contextInfoSupplier == null ? "" : "\n" + contextInfoSupplier.get()));
    }
  }

  private static <T> void reportFailure(@Nullable T existing,
                                        @Nullable T fresh,
                                        @NotNull Class<?> providerClass,
                                        @Nullable Computable<? extends T> recomputeValue,
                                        @NotNull String msg) {
    boolean shouldReport = (ApplicationManager.getApplication().isUnitTestMode()
                           || ourReportedValueClasses.add(providerClass))
                           &&  !"true".equals(System.getProperty("idea.disable.idempotence.checker", "false"));
    if (shouldReport) {
      if (recomputeValue != null) {
        msg += recomputeWithLogging(existing, fresh, recomputeValue);
      }
      LOG.error(PluginException.createByClass(msg + " ("+providerClass+")", null, providerClass));
    }
  }

  private static @NotNull <T> String recomputeWithLogging(@Nullable T existing,
                                                          @Nullable T fresh,
                                                          @NotNull Computable<? extends T> recomputeValue) {
    ResultWithLog<T> rwl = computeWithLogging(recomputeValue);
    T freshest = rwl.result;
    @NonNls String msg = "\n\nRecomputation gives " + objAndClass(freshest);
    if (checkValueEquivalence(existing, freshest) == null) {
      msg += " which is equivalent to 'existing'";
    }
    else if (checkValueEquivalence(fresh, freshest) == null) {
      msg += " which is equivalent to 'fresh'";
    }
    else {
      msg += " which is different from both values";
    }
    if (!rwl.log.isEmpty() && !(freshest instanceof ResultWithLog)) {
      msg += "\nRecomputation log:\n" + rwl.printLog();
    }
    return msg;
  }

  /**
   * Run the given computation with internal logging enabled to help debug {@link #checkEquivalence} failures.
   * @return Both the computation result and the log
   * @see #logTrace(String)
   */
  public static @NotNull <T> ResultWithLog<T> computeWithLogging(@NotNull Computable<? extends T> recomputeValue) {
    List<String> threadLog = ourLog.get();
    boolean outermost = threadLog == null;
    if (outermost) {
      ourLog.set(threadLog = new ArrayList<>());
    }
    try {
      int start = threadLog.size();
      T result = recomputeValue.compute();
      return new ResultWithLog<>(result, new ArrayList<>(threadLog.subList(start, threadLog.size())));
    }
    finally {
      if (outermost) {
        ourLog.remove();
      }
    }
  }

  private static @NonNls String objAndClass(Object o) {
    if (o == null) return "null";

    String s = o instanceof Object[] ? Arrays.toString((Object[])o) : o.toString();
    return s.contains(o.getClass().getSimpleName()) || o instanceof String || o instanceof Number || o instanceof Class
           ? s
           : s + " (" + (o.getClass().isArray() ? o.getClass().getComponentType()+"[]": o.getClass()) + ")";
  }

  private static String checkValueEquivalence(@Nullable Object existing, @Nullable Object fresh) {
    if (existing == fresh) return null;

    String eqMsg = checkClassEquivalence(existing, fresh);
    if (eqMsg != null) return eqMsg;

    Object[] eArray = asArray(existing);
    if (eArray != null) {
      return checkArrayEquivalence(eArray, Objects.requireNonNull(asArray(fresh)), existing);
    }

    if (existing instanceof ResultWithLog) {
      return whichIsField("result", existing, fresh,
                          checkValueEquivalence(((ResultWithLog<?>)existing).getResult(), ((ResultWithLog<?>)fresh).getResult()));
    }

    if (existing instanceof CachedValueBase.Data) {
      return checkCachedValueData((CachedValueBase.Data<?>)existing, (CachedValueBase.Data<?>)fresh);
    }
    if (existing instanceof List || isOrderedSet(existing)) {
      return checkCollectionElements((Collection<?>)existing, (Collection<?>)fresh);
    }
    if (isOrderedMap(existing)) {
      return checkCollectionElements(((Map<?,?>)existing).entrySet(), ((Map<?,?>)fresh).entrySet());
    }
    if (existing instanceof Set) {
      return whichIsField("size", existing, fresh, checkCollectionSizes(((Set<?>)existing).size(), ((Set<?>)fresh).size()));
    }
    if (existing instanceof Map) {
      if (existing instanceof ConcurrentMap) {
        return null; // likely to be filled lazily
      }
      return whichIsField("size", existing, fresh, checkCollectionSizes(((Map<?,?>)existing).size(), ((Map<?,?>)fresh).size()));
    }
    if (isExpectedToHaveSaneEquals(existing) && !existing.equals(fresh)) {
      return reportProblem(existing, fresh);
    }
    if (existing instanceof PsiNamedElement) {
      return checkPsiEquivalence((PsiElement)existing, (PsiElement)fresh);
    }
    if (existing instanceof ResolveResult) {
      PsiElement existingPsi = ((ResolveResult)existing).getElement();
      PsiElement freshPsi = ((ResolveResult)fresh).getElement();
      if (existingPsi != freshPsi) {
        String s = checkClassEquivalence(existingPsi, freshPsi);
        if (s == null) s = checkPsiEquivalence(existingPsi, freshPsi);
        return whichIsField("element", existing, fresh, s);
      }
      return null;
    }
    return null;
  }

  private static boolean isOrderedMap(@NotNull Object o) {
    return o instanceof LinkedHashMap || o instanceof SortedMap;
  }

  private static boolean isOrderedSet(@NotNull Object o) {
    return o instanceof LinkedHashSet || o instanceof SortedSet;
  }

  private static String whichIsField(@NotNull @NonNls String field, @NotNull Object existing, @NotNull Object fresh, @Nullable String msg) {
    return msg == null ? null : appendDetail(msg, "which is `." + field + "` of " + existing + " and " + fresh);
  }

  private static Object @Nullable [] asArray(@NotNull Object o) {
    if (o instanceof Object[]) return (Object[])o;
    if (o instanceof Map.Entry) return new Object[]{((Map.Entry<?,?>)o).getKey(), ((Map.Entry<?,?>)o).getValue()};
    if (o instanceof Pair) return new Object[]{((Pair<?,?>)o).first, ((Pair<?,?>)o).second};
    if (o instanceof Trinity) return new Object[]{((Trinity<?,?,?>)o).first, ((Trinity<?,?,?>)o).second, ((Trinity<?,?,?>)o).third};
    return null;
  }

  private static String checkCachedValueData(@NotNull CachedValueBase.Data<?> existing, @NotNull CachedValueBase.Data<?> fresh) {
    Object[] deps1 = existing.getDependencies();
    Object[] deps2 = fresh.getDependencies();
    Object eValue = existing.get();
    Object fValue = fresh.get();
    if (deps1.length != deps2.length) {
      String msg = reportProblem(deps1.length, deps2.length);
      msg = appendDetail(msg, "which is length of CachedValue dependencies: " + Arrays.toString(deps1) + " and " + Arrays.toString(deps2));
      msg = appendDetail(msg, "where values are  " + objAndClass(eValue) + " and " + objAndClass(fValue));
      return msg;
    }

    return checkValueEquivalence(eValue, fValue);
  }

  private static boolean isExpectedToHaveSaneEquals(@NotNull Object existing) {
    return existing instanceof Comparable
           || existing instanceof Symbol;
  }

  @Contract("null,_->!null;_,null->!null")
  private static String checkClassEquivalence(@Nullable Object existing, @Nullable Object fresh) {
    if (existing == null || fresh == null) {
      return reportProblem(existing, fresh);
    }
    Class<?> c1 = existing.getClass();
    Class<?> c2 = fresh.getClass();
    if (c1 != c2 && !objectsOfDifferentClassesCanStillBeEquivalent(existing, fresh)) {
      return whichIsField("class", existing, fresh, reportProblem(c1, c2));
    }
    return null;
  }

  private static boolean objectsOfDifferentClassesCanStillBeEquivalent(@NotNull Object existing, @NotNull Object fresh) {
    if (existing instanceof Map && fresh instanceof Map && isOrderedMap(existing) == isOrderedMap(fresh)) return true;
    if (existing instanceof Set && fresh instanceof Set && isOrderedSet(existing) == isOrderedSet(fresh)) return true;
    if (existing instanceof List && fresh instanceof List) return true;
    if (existing instanceof PsiNamedElement && fresh instanceof PsiNamedElement) return true; // ClsClassImpl might be equal to PsiClass
    return ContainerUtil.intersects(allSupersWithEquals.get(existing.getClass()), allSupersWithEquals.get(fresh.getClass()));
  }

  @SuppressWarnings("rawtypes")
  private static final Map<Class, Set<Class>> allSupersWithEquals = ConcurrentFactoryMap.createMap(
    clazz -> JBIterable
      .generate(clazz, Class::getSuperclass)
      .filter(c -> c != Object.class && ReflectionUtil.getDeclaredMethod(c, "equals", Object.class) != null)
      .toSet());

  private static String checkPsiEquivalence(@NotNull PsiElement existing, @NotNull PsiElement fresh) {
    if (!existing.equals(fresh) &&
        !existing.isEquivalentTo(fresh) && !fresh.isEquivalentTo(existing) &&
        (seemsToBeResolveTarget(existing) || seemsToBeResolveTarget(fresh))) {
      return reportProblem(existing, fresh);
    }
    return null;
  }

  private static boolean seemsToBeResolveTarget(@NotNull PsiElement psi) {
    if (psi.isPhysical()) return true;
    PsiElement nav = psi.getNavigationElement();
    return nav != null && nav.isPhysical();
  }

  private static String checkCollectionElements(@NotNull Collection<?> existing, @NotNull Collection<?> fresh) {
    if (fresh.isEmpty()) {
      return null; // for cases when an empty collection is cached and then filled lazily on request
    }
    return checkArrayEquivalence(existing.toArray(), fresh.toArray(), existing);
  }

  private static String checkCollectionSizes(int size1, int size2) {
    if (size2 == 0) {
      return null; // for cases when an empty collection is cached and then filled lazily on request
    }
    if (size1 != size2) {
      return reportProblem(size1, size2);
    }
    return null;
  }

  private static String checkArrayEquivalence(Object @NotNull [] a1, Object @NotNull [] a2, @NotNull Object original1) {
    int len1 = a1.length;
    int len2 = a2.length;
    if (len1 != len2) {
      return appendDetail(reportProblem(len1, len2), "which is length of " + Arrays.toString(a1) + " and " + Arrays.toString(a2));
    }
    for (int i = 0; i < len1; i++) {
      String msg = checkValueEquivalence(a1[i], a2[i]);
      if (msg != null) {
        return whichIsField(original1 instanceof Map.Entry ? (i == 0 ? "key" : "value") : i + "th element",
                            Arrays.toString(a1), Arrays.toString(a2), msg);
      }
    }
    return null;
  }

  private static @NotNull String reportProblem(@Nullable Object o1, @Nullable Object o2) {
    return appendDetail("Non-idempotent computation: it returns different results when invoked multiple times or on different threads:",
                        objAndClass(o1) + " != " + objAndClass(o2));
  }

  @Contract(pure = true)
  private static @NotNull String appendDetail(@NotNull @NonNls String message, @NotNull @NonNls String detail) {
    return message + "\n  " + StringUtil.trimLog(detail, 10_000);
  }

  /**
   * @return whether random checks are enabled and it makes sense to call a potentially expensive {@link #applyForRandomCheck} at all.
   */
  public static boolean areRandomChecksEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest();
  }

  /**
   * Useful when your test checks how many times a specific code was called, and random checks make that test flaky.
   */
  @TestOnly
  public static void disableRandomChecksUntil(@NotNull Disposable parentDisposable) {
    rateCheckProperty.get().setValue(0, parentDisposable);
  }

  /**
   * Call this when accessing an already cached value, so that once in a while
   * (depending on "platform.random.idempotence.check.rate" registry value)
   * the computation is re-run and checked for consistency with that cached value.
   */
  public static <T> void applyForRandomCheck(@NotNull T data, @NotNull Object provider, @NotNull Computable<? extends T> recomputeValue) {
    if (areRandomChecksEnabled() && shouldPerformRandomCheck()) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      AtomicInteger prevNesting = ourRandomCheckNesting.get();
      prevNesting.incrementAndGet();
      try {
        T fresh = recomputeValue.compute();
        if (stamp.mayCacheNow()) {
          checkEquivalence(data, fresh, provider.getClass(), recomputeValue);
        }
      }
      finally {
        prevNesting.decrementAndGet();
      }
    }
  }

  private static boolean shouldPerformRandomCheck() {
    int rate = rateCheckProperty.get().asInteger();
    return rate > 0 && ThreadLocalRandom.current().nextInt(rate) == 0 && !ApplicationManagerEx.isInStressTest();
  }

  @TestOnly
  public static boolean isCurrentThreadInsideRandomCheck() {
    return ourRandomCheckNesting.get().get() > 0;
  }

  /**
   * @return whether {@link #logTrace} will actually log anything
   */
  public static boolean isLoggingEnabled() {
    return ourLog.get() != null;
  }

  /**
   * Log a message to help debug {@link #checkEquivalence} failures. When such a failure occurs, the computation can be re-run again
   * with this logging enabled, and the collected log will be included into exception message.
   */
  public static void logTrace(@NotNull @NonNls String message) {
    List<String> log = ourLog.get();
    if (log != null) {
      log.add(message);
    }
  }

  public static final class ResultWithLog<T> {
    private final T result;
    private final List<String> log;

    private ResultWithLog(T result, List<String> log) {
      this.result = result;
      this.log = log;
    }

    public T getResult() {
      return result;
    }

    String printLog() {
      return StringUtil.join(log, s -> "  " + s, "\n");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ResultWithLog)) return false;
      ResultWithLog<?> log = (ResultWithLog<?>)o;
      return Arrays.deepEquals(new Object[]{result}, new Object[]{log.result});
    }

    @Override
    public int hashCode() {
      return Objects.hash(result);
    }

    @Override
    public String toString() {
      return "ResultWithLog{" + result + (log.isEmpty() ? "" : ", log='\n" + printLog() + '\'') + '}';
    }
  }

}
