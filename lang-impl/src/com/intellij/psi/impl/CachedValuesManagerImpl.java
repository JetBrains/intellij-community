package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class CachedValuesManagerImpl extends CachedValuesManager {
  private final PsiManager myManager;

  public CachedValuesManagerImpl(PsiManager manager) {
    myManager = manager;
  }

  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return new CachedValueImpl<T>(myManager, provider, trackValue);
  }

  public <T,P> ParameterizedCachedValue<T,P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T,P> provider, boolean trackValue) {
    return new ParameterizedCachedValueImpl<T,P>(myManager, provider, trackValue);
  }
}
