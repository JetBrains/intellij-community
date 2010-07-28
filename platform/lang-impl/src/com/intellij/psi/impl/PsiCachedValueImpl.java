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

package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.*;

/**
 * @author Dmitry Avdeev
 */
public class PsiCachedValueImpl<T> extends PsiCachedValue<T> implements CachedValue<T>  {
  private final CachedValueProvider<T> myProvider;

  public PsiCachedValueImpl(PsiManager manager, CachedValueProvider<T> provider) {
    super(manager);
    myProvider = provider;
  }
  @Nullable
  public T getValue() {

    r.lock();

    T value;
    try {
      value = getUpToDateOrNull();
      if (value != null) {
        return value == NULL ? null : value;
      }
    } finally {
      r.unlock();
    }

    w.lock();

    try {
      value = getUpToDateOrNull();
      if (value != null) {
        return value == NULL ? null : value;
      }

      CachedValueProvider.Result<T> result = myProvider.compute();
      value = result == null ? null : result.getValue();

      setValue(value, result);

      return value;
    }
    finally {
      w.unlock();
    }
  }

  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }
}
