// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ProfilingInfo;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.NotNullList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class CachedValueBase<T> {
  private static final Logger LOG = Logger.getInstance(CachedValueImpl.class);
  private final boolean myTrackValue;
  private volatile SoftReference<Data<T>> myData;

  protected CachedValueBase(boolean trackValue) {
    myTrackValue = trackValue;
  }

  @NotNull
  private Data<T> computeData(@Nullable CachedValueProvider.Result<T> result) {
    if (result == null) {
      return new Data<>(null, ArrayUtilRt.EMPTY_OBJECT_ARRAY, ArrayUtil.EMPTY_LONG_ARRAY);
    }
    T value = result.getValue();
    Object[] inferredDependencies = normalizeDependencies(result);
    long[] inferredTimeStamps = new long[inferredDependencies.length];
    for (int i = 0; i < inferredDependencies.length; i++) {
      inferredTimeStamps[i] = getTimeStamp(inferredDependencies[i]);
    }

    if (CachedValueProfiler.canProfile()) {
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
      setData(updatedValue);
      return updatedValue;
    }
    return expected;
  }

  private synchronized void setData(@Nullable Data<T> data) {
    myData = data == null ? null : new SoftReference<>(data);
  }

  protected Object @NotNull [] normalizeDependencies(@NotNull CachedValueProvider.Result<T> result) {
    Object[] items = result.getDependencyItems();
    T value = result.getValue();
    Object[] rawDependencies = myTrackValue && value != null ? ArrayUtil.append(items, value) : items;

    List<Object> flattened = new NotNullList<>(rawDependencies.length);
    collectDependencies(flattened, rawDependencies);
    return ArrayUtil.toObjectArray(flattened);
  }

  public void clear() {
    setData(null);
  }

  public boolean hasUpToDateValue() {
    return getUpToDateOrNull() != null;
  }

  @Nullable
  public final Data<T> getUpToDateOrNull() {
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
  private Data<T> getRawData() {
    return SoftReference.dereference(myData);
  }

  protected boolean isUpToDate(@NotNull Data data) {
    for (int i = 0; i < data.myDependencies.length; i++) {
      Object dependency = data.myDependencies[i];
      if (isDependencyOutOfDate(dependency, data.myTimeStamps[i])) return false;
    }

    return true;
  }

  protected boolean isDependencyOutOfDate(@NotNull Object dependency, long oldTimeStamp) {
    if (dependency instanceof CachedValueBase) {
      return !((CachedValueBase)dependency).hasUpToDateValue();
    }
    final long timeStamp = getTimeStamp(dependency);
    return timeStamp < 0 || timeStamp != oldTimeStamp;
  }

  private static void collectDependencies(@NotNull List<Object> resultingDeps, Object @NotNull [] dependencies) {
    for (Object dependency : dependencies) {
      if (dependency == ObjectUtils.NULL) continue;
      if (dependency instanceof Object[]) {
        collectDependencies(resultingDeps, (Object[])dependency);
      }
      else {
        resultingDeps.add(dependency);
      }
    }
  }

  protected long getTimeStamp(@NotNull Object dependency) {
    if (dependency instanceof VirtualFile) {
      return ((VirtualFile)dependency).getModificationStamp();
    }
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

  public T setValue(@NotNull CachedValueProvider.Result<T> result) {
    Data<T> data = computeData(result);
    setData(data);
    return data.getValue();
  }

  public abstract boolean isFromMyProject(@NotNull Project project);

  public abstract Object getValueProvider();

  protected static class Data<T> implements Getter<T> {
    private final T myValue;
    private final Object @NotNull [] myDependencies;
    private final long @NotNull [] myTimeStamps;

    Data(final T value, Object @NotNull [] dependencies, long @NotNull [] timeStamps) {
      myValue = value;
      myDependencies = dependencies;
      myTimeStamps = timeStamps;
    }

    public Object @NotNull [] getDependencies() {
      return myDependencies;
    }

    public long @NotNull [] getTimeStamps() {
      return myTimeStamps;
    }

    @Override
    public final T get() {
      return getValue();
    }

    public T getValue() {
      return myValue;
    }
  }

  private static final class ProfilingData<T> extends Data<T> {
    @NotNull private final ProfilingInfo myProfilingInfo;

    private ProfilingData(T value,
                          Object @NotNull [] dependencies,
                          long @NotNull [] timeStamps,
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
      if (IdempotenceChecker.areRandomChecksEnabled()) {
        IdempotenceChecker.applyForRandomCheck(data, getValueProvider(), () -> computeData(doCompute(param)));
      }
      return data.getValue();
    }

    RecursionGuard.StackStamp stamp = RecursionManager.markStack();

    Computable<Data<T>> calcData = () -> computeData(doCompute(param));
    data = RecursionManager.doPreventingRecursion(this, true, calcData);
    if (data == null) {
      data = calcData.compute();
    }
    else if (stamp.mayCacheNow()) {
      while (true) {
        Data<T> alreadyComputed = getRawData();
        boolean reuse = alreadyComputed != null && isUpToDate(alreadyComputed);
        if (reuse) {
          IdempotenceChecker.checkEquivalence(alreadyComputed, data, getValueProvider().getClass(), calcData);
        }
        Data<T> toReturn = cacheOrGetData(alreadyComputed, reuse ? null : data);
        if (toReturn != null) {
          return toReturn.getValue();
        }
      }
    }
    return data.getValue();
  }

  protected abstract <P> CachedValueProvider.Result<T> doCompute(P param);

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + getValueProvider() + "}";
  }
}
