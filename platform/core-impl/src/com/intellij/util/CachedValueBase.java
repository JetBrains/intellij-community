/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.util.CachedValueProvider;
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
    if (dependencies == null) {
      return new Data<>(value, null, null);
    }

    TLongArrayList timeStamps = new TLongArrayList(dependencies.length);
    List<Object> deps = new NotNullList<>(dependencies.length);
    collectDependencies(timeStamps, deps, dependencies);

    return new Data<>(value, ArrayUtil.toObjectArray(deps), timeStamps.toNativeArray());
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
  protected Object[] getDependenciesPlusValue(CachedValueProvider.Result<T> result) {
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
    return getUpToDateOrNull(false) != null;
  }

  @Nullable
  private Data<T> getUpToDateOrNull(boolean dispose) {
    final Data<T> data = getRawData();

    if (data != null) {
      if (isUpToDate(data)) {
        return data;
      }
      if (dispose && data.myValue instanceof Disposable && compareAndClearData(data)) {
        Disposer.dispose((Disposable)data.myValue);
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
    return data.myValue;
  }

  protected void valueUpdated(@Nullable Object[] dependencies) {}

  public abstract boolean isFromMyProject(Project project);

  protected static class Data<T> implements Disposable {
    private final T myValue;
    private final Object[] myDependencies;
    private final long[] myTimeStamps;

    public Data(final T value, final Object[] dependencies, final long[] timeStamps) {
      myValue = value;
      myDependencies = dependencies;
      myTimeStamps = timeStamps;
    }

    @Override
    public void dispose() {
      if (myValue instanceof Disposable) {
        Disposer.dispose((Disposable)myValue);
      }
    }
  }

  @Nullable
  protected <P> T getValueWithLock(P param) {
    Data<T> data = getUpToDateOrNull(true);
    if (data != null) {
      return data.myValue;
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
          return toReturn.myValue;
        }
      }
    }
    return data.myValue;
  }

  protected abstract <P> CachedValueProvider.Result<T> doCompute(P param);

}
