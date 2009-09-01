package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PsiCachedValueImpl<T> extends PsiCachedValue<T> implements CachedValue<T>  {
  private CachedValueProvider<T> myProvider;

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
