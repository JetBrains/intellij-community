// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ProfilingInfo;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.NotNullList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class CachedValueBase<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.CachedValueImpl");
  private volatile SoftReference<Data<T>> myData;

  private Data<T> computeData(@Nullable CachedValueProvider.Result<T> result) {
    T value = result == null ? null : result.getValue();
    Object[] dependencies = getDependencies(result);

    Object[] inferredDependencies;
    long[] inferredTimeStamps;
    if (dependencies == null) {
      inferredDependencies = null;
      inferredTimeStamps = null;
    }
    else {
      TLongArrayList timeStamps = new TLongArrayList(dependencies.length);
      List<Object> deps = new NotNullList<>(dependencies.length);
      collectDependencies(timeStamps, deps, dependencies);

      inferredDependencies = ArrayUtil.toObjectArray(deps);
      inferredTimeStamps = timeStamps.toNativeArray();
    }

    if (result != null && CachedValueProfiler.canProfile()) {
      ProfilingInfo profilingInfo = CachedValueProfiler.getInstance().getTemporaryInfo(result);
      if (profilingInfo != null) {
        return new ProfilingData<>(value, inferredDependencies, inferredTimeStamps, profilingInfo);
      }
    }

    return new Data<>(value, inferredDependencies, inferredTimeStamps);
  }

  @Nullable
  private synchronized Data<T> cacheOrGetData(@Nullable Data<T> expected, @Nullable Data<T> updatedValue) {
    if (expected != getRawData()) return null;

    if (updatedValue != null) {
      myData = new SoftReference<>(updatedValue);
      return updatedValue;
    }
    return expected;
  }

  private synchronized void setData(@Nullable Data<T> data) {
    myData = new SoftReference<>(data);
  }

  private synchronized boolean compareAndClearData(Data<T> expected) {
    if (getRawData() == expected) {
      myData = null;
      return true;
    }
    return false;
  }

  @Nullable
  protected Object[] getDependencies(CachedValueProvider.Result<T> result) {
    return result == null ? null : result.getDependencyItems();
  }

  @Nullable
  protected Object[] getDependenciesPlusValue(CachedValueProvider.Result<? extends T> result) {
    if (result == null) {
      return null;
    }
    else {
      Object[] items = result.getDependencyItems();
      T value = result.getValue();
      return value == null ? items : ArrayUtil.append(items, value);
    }
  }

  public void clear() {
    setData(null);
  }

  public boolean hasUpToDateValue() {
    return getUpToDateOrNull() != null;
  }

  @Nullable
  final Data<T> getUpToDateOrNull() {
    Data<T> data = getRawData();

    if (data != null) {
      if (isUpToDate(data)) {
        return data;
      }
      if (data instanceof ProfilingData) {
        ((ProfilingData<T>)data).myProfilingInfo.valueDisposed();
      }
    }
    return null;
  }

  @Nullable
  final Data<T> getRawData() {
    return SoftReference.dereference(myData);
  }

  protected boolean isUpToDate(@NotNull Data data) {
    if (data.myTimeStamps == null) return true;

    for (int i = 0; i < data.myDependencies.length; i++) {
      Object dependency = data.myDependencies[i];
      if (dependency == null) continue;
      if (isDependencyOutOfDate(dependency, data.myTimeStamps[i])) return false;
    }

    return true;
  }

  protected boolean isDependencyOutOfDate(Object dependency, long oldTimeStamp) {
    if (dependency instanceof CachedValueBase) {
      return !((CachedValueBase)dependency).hasUpToDateValue();
    }
    final long timeStamp = getTimeStamp(dependency);
    return timeStamp < 0 || timeStamp != oldTimeStamp;
  }

  private void collectDependencies(TLongArrayList timeStamps, List<Object> resultingDeps, Object[] dependencies) {
    for (Object dependency : dependencies) {
      if (dependency == null || dependency == ObjectUtils.NULL) continue;
      if (dependency instanceof Object[]) {
        collectDependencies(timeStamps, resultingDeps, (Object[])dependency);
      }
      else {
        resultingDeps.add(dependency);
        timeStamps.add(getTimeStamp(dependency));
      }
    }
  }

  protected long getTimeStamp(Object dependency) {
    if (dependency instanceof ModificationTracker) {
      return ((ModificationTracker)dependency).getModificationCount();
    }
    else if (dependency instanceof Reference){
      final Object original = ((Reference)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    else if (dependency instanceof Ref) {
      final Object original = ((Ref)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    else if (dependency instanceof Document) {
      return ((Document)dependency).getModificationStamp();
    }
    else if (dependency instanceof CachedValueBase) {
      // to check for up to date for a cached value dependency we use .isUpToDate() method, not the timestamp
      return 0;
    }
    else {
      LOG.error("Wrong dependency type: " + dependency.getClass());
      return -1;
    }
  }

  public T setValue(final CachedValueProvider.Result<T> result) {
    Data<T> data = computeData(result);
    setData(data);
    valueUpdated(result.getDependencyItems());
    return data.getValue();
  }

  protected void valueUpdated(@Nullable Object[] dependencies) {}

  public abstract boolean isFromMyProject(Project project);

  protected static class Data<T> {
    private final T myValue;
    private final Object[] myDependencies;
    private final long[] myTimeStamps;

    Data(final T value, final Object[] dependencies, final long[] timeStamps) {
      myValue = value;
      myDependencies = dependencies;
      myTimeStamps = timeStamps;
    }

    public T getValue() {
      return myValue;
    }
  }

  private static class ProfilingData<T> extends Data<T> {
    @NotNull private final ProfilingInfo myProfilingInfo;

    private ProfilingData(T value,
                          Object[] dependencies,
                          long[] timeStamps,
                          @NotNull ProfilingInfo profilingInfo) {
      super(value, dependencies, timeStamps);
      myProfilingInfo = profilingInfo;
    }

    @Override
    public T getValue() {
      myProfilingInfo.valueUsed();
      return super.getValue();
    }
  }

  @Nullable
  protected <P> T getValueWithLock(P param) {
    Data<T> data = getUpToDateOrNull();
    if (data != null) {
      return data.getValue();
    }

    RecursionGuard.StackStamp stamp = RecursionManager.createGuard("cachedValue").markStack();

    // compute outside lock to avoid deadlock
    data = computeData(doCompute(param));

    if (stamp.mayCacheNow()) {
      while (true) {
        Data<T> alreadyComputed = getRawData();
        boolean reuse = alreadyComputed != null && isUpToDate(alreadyComputed);
        Data<T> toReturn = cacheOrGetData(alreadyComputed, reuse ? null : data);
        if (toReturn != null) {
          valueUpdated(toReturn.myDependencies);
          return toReturn.getValue();
        }
      }
    }
    return data.getValue();
  }

  protected abstract <P> CachedValueProvider.Result<T> doCompute(P param);
}
