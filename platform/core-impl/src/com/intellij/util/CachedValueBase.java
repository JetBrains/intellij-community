/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class CachedValueBase<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.CachedValueImpl");
  private volatile SoftReference<Data<T>> myData = null;

  protected Data<T> computeData(T value, Object[] dependencies) {
    if (dependencies == null) {
      return new Data<T>(value, null, null);
    }

    TLongArrayList timeStamps = new TLongArrayList(dependencies.length);
    List<Object> deps = new ArrayList<Object>(dependencies.length);
    collectDependencies(timeStamps, deps, dependencies);

    return new Data<T>(value, ArrayUtil.toObjectArray(deps), timeStamps.toNativeArray());
  }

  protected void setValue(final T value, final CachedValueProvider.Result<T> result) {
    myData = new SoftReference<Data<T>>(computeData(value == null ? (T)ObjectUtils.NULL : value, getDependencies(result)));
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
      return value == null ? items : items == null ? new Object[] {value}: ArrayUtil.append(items, value);
    }
  }

  public void clear() {
    myData = null;
  }

  public boolean hasUpToDateValue() {
    return getUpToDateOrNull(false) != null;
  }

  @Nullable
  private T getUpToDateOrNull(boolean dispose) {
    final Data<T> data = getData();

    if (data != null) {
      T value = data.myValue;
      if (isUpToDate(data)) {
        return value;
      }
      if (dispose && value instanceof Disposable) {
        Disposer.dispose((Disposable)value);
      }
    }
    return null;
  }

  @Nullable
  private Data<T> getData() {
    final SoftReference<Data<T>> ref = myData;
    return ref == null ? null : ref.get();
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
    T value = result == null ? null : result.getValue();
    setValue(value, result);
    return value;
  }

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
    T value = getUpToDateOrNull(true);
    if (value != null) {
      return value == ObjectUtils.NULL ? null : value;
    }

    RecursionGuard.StackStamp stamp = RecursionManager.createGuard("cachedValue").markStack();

    // compute outside lock to avoid deadlock
    CachedValueProvider.Result<T> result = doCompute(param);

    if (stamp.mayCacheNow()) {
      return setValue(result);
    }
    return result == null ? null : result.getValue();
  }

  protected abstract <P> CachedValueProvider.Result<T> doCompute(P param);

}
