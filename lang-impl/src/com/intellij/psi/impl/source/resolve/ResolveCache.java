package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.reference.SoftReference;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentWeakHashMap;

import java.lang.ref.Reference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolveCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.ResolveCache");
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");
  private static final Key<List<Thread>> IS_BEING_RESOLVED_KEY = Key.create("ResolveCache.IS_BEING_RESOLVED_KEY");

  private final Map<PsiPolyVariantReference,Reference<ResolveResult[]>>[] myPolyVariantResolveMaps = new Map[4];
  private final Map<PsiReference,Reference<PsiElement>>[] myResolveMaps = new Map[4];
  private final AtomicInteger myClearCount = new AtomicInteger(0);
  private final PsiManagerEx myManager;

  private final List<Runnable> myRunnablesToRunOnDropCaches = new CopyOnWriteArrayList<Runnable>();

  public static interface AbstractResolver<Ref extends PsiReference,Result> {
    Result resolve(Ref ref, boolean incompleteCode);
  }
  public static interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
  }

  public static interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
  }

  public ResolveCache(PsiManagerEx manager) {
    myManager = manager;
    myPolyVariantResolveMaps[0] = getOrCreateWeakMap(JAVA_RESOLVE_MAP, true);
    myPolyVariantResolveMaps[1] = getOrCreateWeakMap(JAVA_RESOLVE_MAP_INCOMPLETE, true);
    myResolveMaps[0] = getOrCreateWeakMap(RESOLVE_MAP, true);
    myResolveMaps[1] = getOrCreateWeakMap(RESOLVE_MAP_INCOMPLETE, true);

    myPolyVariantResolveMaps[2] = getOrCreateWeakMap(JAVA_RESOLVE_MAP, false);
    myPolyVariantResolveMaps[3] = getOrCreateWeakMap(JAVA_RESOLVE_MAP_INCOMPLETE, false);

    myResolveMaps[2] = getOrCreateWeakMap(RESOLVE_MAP, false);
    myResolveMaps[3] = getOrCreateWeakMap(RESOLVE_MAP_INCOMPLETE, false);
  }

  public void clearCache() {
    myClearCount.incrementAndGet();
    myPolyVariantResolveMaps[0].clear();
    myPolyVariantResolveMaps[1].clear();
    myResolveMaps[0].clear();
    myResolveMaps[1].clear();

    myPolyVariantResolveMaps[2].clear();
    myPolyVariantResolveMaps[3].clear();

    myResolveMaps[2].clear();
    myResolveMaps[3].clear();

    for (Runnable r : myRunnablesToRunOnDropCaches) {
      r.run();
    }
  }

  public void addRunnableToRunOnDropCaches(Runnable r) {
    myRunnablesToRunOnDropCaches.add(r);
  }

  private <Ref extends PsiReference, Result> Result resolve(Ref ref,
                                        AbstractResolver<Ref, Result> resolver,
                                        Map<? super Ref,Reference<Result>>[] maps,
                                        boolean needToPreventRecursion,
                                        boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    int clearCountOnStart = myClearCount.intValue();

    boolean physical = ref.getElement().isPhysical();
    Result result = getCached(ref, maps, physical, incompleteCode);
    if (result != null) {
      return result;
    }

    if (incompleteCode) {
      result = resolve(ref, resolver, maps, needToPreventRecursion, false);
      if (result != null && !(result instanceof Object[] && ((Object[])result).length == 0)) {
        cache(ref, result, maps, physical, incompleteCode, clearCountOnStart);
        return result;
      }
    }

    if (needToPreventRecursion && !lockElement(ref)) return null;
    try {
      result = resolver.resolve(ref, incompleteCode);
    }
    finally {
      if (needToPreventRecursion) {
        unlockElement(ref);
      }
    }
    cache(ref, result, maps, physical, incompleteCode, clearCountOnStart);
    return result;
  }

   public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching(T ref,
                                            PolyVariantResolver<T> resolver,
                                            boolean needToPreventRecursion,
                                            boolean incompleteCode) {
    ResolveResult[] result = resolve(ref, resolver, myPolyVariantResolveMaps, needToPreventRecursion, incompleteCode);
    return result == null ? ResolveResult.EMPTY_ARRAY : result;
  }

  public PsiElement resolveWithCaching(PsiReference ref,
                                       Resolver resolver,
                                       boolean needToPreventRecursion,
                                       boolean incompleteCode) {
    return resolve(ref, resolver, myResolveMaps, needToPreventRecursion, incompleteCode);
  }

  private static boolean lockElement(PsiReference ref) {
    synchronized (IS_BEING_RESOLVED_KEY) {
      PsiElement elt = ref.getElement();

      List<Thread> lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
      final Thread currentThread = Thread.currentThread();
      if (lockingThreads == null) {
        lockingThreads = new SmartList<Thread>();
        elt.putUserData(IS_BEING_RESOLVED_KEY, lockingThreads);
      }
      else {
        if (lockingThreads.contains(currentThread)) return false;
      }
      lockingThreads.add(currentThread);
    }
    return true;
  }

  private static void unlockElement(PsiReference ref) {
    synchronized (IS_BEING_RESOLVED_KEY) {
      PsiElement elt = ref.getElement();

      List<Thread> lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
      if (lockingThreads == null) return;
      final Thread currentThread = Thread.currentThread();
      lockingThreads.remove(currentThread);
      if (lockingThreads.isEmpty()) {
        elt.putUserData(IS_BEING_RESOLVED_KEY, null);
      }
    }
  }

  //for Visual Fabrique
  public void clearResolveCaches(PsiReference ref) {
    myClearCount.incrementAndGet();
    final boolean physical = ref.getElement().isPhysical();
    if (ref instanceof PsiPolyVariantReference) {
      cache((PsiPolyVariantReference)ref, null, myPolyVariantResolveMaps, physical, false, myClearCount.intValue());
      cache((PsiPolyVariantReference)ref, null, myPolyVariantResolveMaps, physical, true, myClearCount.intValue());
    }
  }


  private static int getIndex(boolean physical, boolean ic){
    return (physical ? 0 : 1) << 1 | (ic ? 1 : 0);
  }

  private static <Ref,Result>Result getCached(Ref ref, Map<? super Ref,Reference<Result>>[] maps, boolean physical, boolean ic){
    int index = getIndex(physical, ic);
    Reference<Result> reference = maps[index].get(ref);
    if(reference == null) return null;
    return reference.get();
  }
  private <Ref,Result> void cache(Ref ref, Result result, Map<? super Ref,Reference<Result>>[] maps, boolean physical, boolean incompleteCode, final int clearCountOnStart) {
    if (clearCountOnStart != myClearCount.intValue() && result != null) return;

    int index = getIndex(physical, incompleteCode);
    maps[index].put(ref, new SoftReference<Result>(result));
  }

  public <K,V> ConcurrentMap<K,V> getOrCreateWeakMap(final Key<MapPair<K, V>> key, boolean forPhysical) {
    MapPair<K, V> pair = myManager.getUserData(key);
    if (pair == null) {
      pair = myManager.putUserDataIfAbsent(key, new MapPair<K,V>());

      final MapPair<K, V> _pair = pair;
      myManager.registerRunnableToRunOnChange(
        new Runnable() {
          public void run() {
            myClearCount.incrementAndGet();
            _pair.physicalMap.clear();
          }
        }
      );
      myManager.registerRunnableToRunOnAnyChange(
        new Runnable() {
          public void run() {
            myClearCount.incrementAndGet();
            _pair.nonPhysicalMap.clear();
          }
        }
      );
    }
    return forPhysical ? pair.physicalMap : pair.nonPhysicalMap;
  }

  public static class MapPair<K,V>{
    public final ConcurrentMap<K,V> physicalMap = new ConcurrentWeakHashMap<K, V>();
    public final ConcurrentMap<K,V> nonPhysicalMap = new ConcurrentWeakHashMap<K, V>();
  }
}