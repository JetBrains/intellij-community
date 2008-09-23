package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentWeakHashMap;

import java.lang.ref.Reference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ResolveCache {
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP = Key.create("ResolveCache.JAVA_RESOLVE_MAP");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP = Key.create("ResolveCache.RESOLVE_MAP");
  private static final Key<MapPair<PsiPolyVariantReference, Reference<ResolveResult[]>>> JAVA_RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.JAVA_RESOLVE_MAP_INCOMPLETE");
  private static final Key<MapPair<PsiReference, Reference<PsiElement>>> RESOLVE_MAP_INCOMPLETE = Key.create("ResolveCache.RESOLVE_MAP_INCOMPLETE");

  private final Map<PsiPolyVariantReference,Reference<ResolveResult[]>>[] myPolyVariantResolveMaps = new Map[4];
  private final Map<PsiReference,Reference<PsiElement>>[] myResolveMaps = new Map[4];
  private final AtomicInteger myClearCount = new AtomicInteger(0);
  private final PsiManagerEx myManager;

  private final List<Runnable> myRunnablesToRunOnDropCaches = new CopyOnWriteArrayList<Runnable>();

  public interface AbstractResolver<Ref extends PsiReference,Result> {
    Result resolve(Ref ref, boolean incompleteCode);
  }
  public interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
  }

  public interface Resolver extends AbstractResolver<PsiReference,PsiElement>{
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

  private <TRef extends PsiReference, TResult> TResult resolve(TRef ref,
                                        AbstractResolver<TRef, TResult> resolver,
                                        Map<? super TRef,Reference<TResult>>[] maps,
                                        boolean needToPreventRecursion,
                                        boolean incompleteCode) {
    ProgressManager.getInstance().checkCanceled();

    int clearCountOnStart = myClearCount.intValue();

    boolean physical = ref.getElement().isPhysical();
    TResult result = getCached(ref, maps, physical, incompleteCode);
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

  private static final Key<Thread[]> IS_BEING_RESOLVED_KEY = Key.create("ResolveCache.IS_BEING_RESOLVED_KEY");
  private static boolean lockElement(PsiReference ref) {
    PsiElement elt = ref.getElement();

    final Thread currentThread = Thread.currentThread();
    while (true) {
      Thread[] lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
      Thread[] newThreads;
      if (lockingThreads == null) {
        newThreads = new Thread[]{currentThread};
        if (((UserDataHolderEx)elt).putUserDataIfAbsent(IS_BEING_RESOLVED_KEY, newThreads) == newThreads) {
          break;
        }
      }
      else {
        if (ArrayUtil.find(lockingThreads, currentThread) != -1) return false;
        newThreads = ArrayUtil.append(lockingThreads, currentThread);
        if (((UserDataHolderEx)elt).replace(IS_BEING_RESOLVED_KEY, lockingThreads, newThreads)) {
          break;
        }
      }
    }
    return true;
  }

  private static void unlockElement(PsiReference ref) {
    PsiElement elt = ref.getElement();
    final Thread currentThread = Thread.currentThread();
    while (true) {
      Thread[] lockingThreads = elt.getUserData(IS_BEING_RESOLVED_KEY);
      Thread[] newThreads;
      if (lockingThreads.length == 1) {
        newThreads = null;
      }
      else {
        newThreads = ArrayUtil.remove(lockingThreads, currentThread);
      }
      if (((UserDataHolderEx)elt).replace(IS_BEING_RESOLVED_KEY, lockingThreads, newThreads)) {
        break;
      }
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

  private <Ref extends PsiReference, Result> void cache(Ref ref, Result result, Map<? super Ref,Reference<Result>>[] maps, boolean physical, boolean incompleteCode, final int clearCountOnStart) {
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
