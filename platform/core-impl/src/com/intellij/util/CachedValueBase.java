// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.NotNullList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class CachedValueBase<T> {
  private final boolean myTrackValue;

  protected CachedValueBase(boolean trackValue) {
    myTrackValue = trackValue;
  }

  protected abstract @Nullable Data<T> getRawData();

  protected abstract void setData(@Nullable Data<T> data);

  private @NotNull Data<T> computeData(@NotNull Computable<CachedValueProvider.Result<T>> doCompute) {
    CachedValueProvider.Result<T> result;
    CachedValueProfiler.ValueTracker tracker;
    if (CachedValueProfiler.isProfiling()) {
      try (CachedValueProfiler.Frame frame = CachedValueProfiler.newFrame()) {
        result = doCompute.compute();
        tracker = result == null ? null : frame.newValueTracker(result);
      }
    }
    else {
      result = doCompute.compute();
      tracker = null;
    }
    if (result == null) {
      return new Data<>(null, ArrayUtilRt.EMPTY_OBJECT_ARRAY, ArrayUtil.EMPTY_LONG_ARRAY, null);
    }
    T value = result.getValue();
    Object[] inferredDependencies = normalizeDependencies(value, result.getDependencyItems());
    long[] inferredTimeStamps = new long[inferredDependencies.length];
    for (int i = 0; i < inferredDependencies.length; i++) {
      inferredTimeStamps[i] = getTimeStamp(inferredDependencies[i]);
    }
    return new Data<>(value, inferredDependencies, inferredTimeStamps, tracker);
  }

  private synchronized @Nullable Data<T> cacheOrGetData(@Nullable Data<T> expected, @Nullable Data<T> updatedValue) {
    if (expected != getRawData()) return null;

    if (updatedValue != null) {
      setRawData(updatedValue);
      return updatedValue;
    }
    return expected;
  }

  private synchronized void setRawData(@Nullable Data<T> data) {
    setData(data);
  }

  protected Object @NotNull [] normalizeDependencies(@Nullable T value, Object @NotNull [] dependencyItems) {
    List<Object> flattened = new NotNullList<>(dependencyItems.length+1);
    collectDependencies(dependencyItems, flattened);
    if (myTrackValue && value != null) {
      if (value instanceof Object[]) {
        collectDependencies((Object[])value, flattened);
      }
      else {
        flattened.add(value);
      }
    }
    return ArrayUtil.toObjectArray(flattened);
  }

  public void clear() {
    setRawData(null);
  }

  public boolean hasUpToDateValue() {
    return getUpToDateOrNull() != null;
  }

  public final @Nullable Data<T> getUpToDateOrNull() {
    Data<T> data = getRawData();
    return data != null && checkUpToDate(data) ? data : null;
  }

  private boolean checkUpToDate(@NotNull Data<T> data) {
    if (isUpToDate(data)) {
      return true;
    }
    if (data.trackingInfo != null) {
      data.trackingInfo.onValueInvalidated();
    }
    return false;
  }

  protected boolean isUpToDate(@NotNull Data<T> data) {
    for (int i = 0; i < data.myDependencies.length; i++) {
      Object dependency = data.myDependencies[i];
      if (isDependencyOutOfDate(dependency, data.myTimeStamps[i])) return false;
    }

    return true;
  }

  protected boolean isDependencyOutOfDate(@NotNull Object dependency, long oldTimeStamp) {
    if (dependency instanceof CachedValueBase) {
      return !((CachedValueBase<?>)dependency).hasUpToDateValue();
    }
    final long timeStamp = getTimeStamp(dependency);
    return timeStamp < 0 || timeStamp != oldTimeStamp;
  }

  private static void collectDependencies(Object @NotNull [] dependencies, @NotNull List<? super Object> resultingDeps) {
    for (Object dependency : dependencies) {
      if (dependency == ObjectUtils.NULL) continue;
      if (dependency instanceof Object[]) {
        collectDependencies((Object[])dependency, resultingDeps);
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
      Object original = ((Reference<?>)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    else if (dependency instanceof Ref) {
      Object original = ((Ref<?>)dependency).get();
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
      Logger.getInstance(CachedValueBase.class).error("Wrong dependency type: " + dependency.getClass());
      return -1;
    }
  }

  public T setValue(@NotNull CachedValueProvider.Result<T> result) {
    Data<T> data = computeData(() -> result);
    setRawData(data);
    return data.getValue();
  }

  public abstract boolean isFromMyProject(@NotNull Project project);

  public abstract @NotNull Object getValueProvider();

  private static final Object[] PSI_MODIFICATION_DEPENDENCIES = new Object[] { PsiModificationTracker.MODIFICATION_COUNT };

  public static final class Data<T> implements Getter<T> {
    private final T myValue;
    private final Object @NotNull [] myDependencies;
    private final long @NotNull [] myTimeStamps;
    final @Nullable CachedValueProfiler.ValueTracker trackingInfo;

    Data(T value,
         Object @NotNull [] dependencies,
         long @NotNull [] timeStamps,
         @Nullable CachedValueProfiler.ValueTracker trackingInfo) {
      myValue = value;

      if (dependencies.length == 1 && dependencies[0] == PsiModificationTracker.MODIFICATION_COUNT) {
        // there is no sense in storing hundreds of new arrays of [PsiModificationTracker.MODIFICATION_COUNT]
        myDependencies = PSI_MODIFICATION_DEPENDENCIES;
      }
      else {
        myDependencies = dependencies;
      }

      myTimeStamps = timeStamps;
      this.trackingInfo = trackingInfo;
    }

    public Object @NotNull [] getDependencies() {
      return myDependencies;
    }

    public long @NotNull [] getTimeStamps() {
      return myTimeStamps;
    }

    @Override
    public T get() {
      return getValue();
    }

    public T getValue() {
      if (trackingInfo != null) {
        trackingInfo.onValueUsed();
      }
      return myValue;
    }
  }

  protected @Nullable <P> T getValueWithLock(P param) {
    Data<T> data = getUpToDateOrNull();
    if (data != null) {
      if (IdempotenceChecker.areRandomChecksEnabled()) {
        IdempotenceChecker.applyForRandomCheck(data, getValueProvider(), () -> computeData(() -> doCompute(param)));
      }
      return data.getValue();
    }

    RecursionGuard.StackStamp stamp = RecursionManager.markStack();

    Computable<Data<T>> calcData = () -> computeData(() -> doCompute(param));
    data = RecursionManager.doPreventingRecursion(this, true, calcData);
    if (data == null) {
      data = calcData.compute();
    }
    else if (stamp.mayCacheNow()) {
      while (true) {
        Data<T> alreadyComputed = getRawData();
        boolean reuse = alreadyComputed != null && checkUpToDate(alreadyComputed);
        if (reuse) {
          IdempotenceChecker.checkEquivalence(alreadyComputed, data, getValueProvider().getClass(), calcData);
        }
        Data<T> toReturn = cacheOrGetData(alreadyComputed, reuse ? null : data);
        if (toReturn != null) {
          if (data != toReturn && data.trackingInfo != null) {
            data.trackingInfo.onValueRejected();
          }
          return toReturn.getValue();
        }
      }
    }
    return data.getValue();
  }

  protected abstract <P> CachedValueProvider.@Nullable Result<T> doCompute(P param);

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + getValueProvider() + "}";
  }
}
