package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.*;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  @Nullable
  public <T, D extends UserDataHolder> T getCachedValue(@NotNull D dataHolder,
                              @NotNull Key<CachedValue<T>> key,
                              @NotNull CachedValueProvider<T> provider,
                              boolean trackValue) {

    CachedValue<T> value;
    if (dataHolder instanceof UserDataHolderEx) {
      UserDataHolderEx dh = (UserDataHolderEx)dataHolder;
      value = dh.getUserData(key);
      if (value instanceof CachedValueImpl && !((CachedValueImpl)value).isFromMyProject(myManager.getProject())) {
        value = null;
        dataHolder.putUserData(key, null);
      }
      if (value == null) {
        value = createCachedValue(provider, trackValue);
        assert ((CachedValueImpl)value).isFromMyProject(myManager.getProject());
        value = dh.putUserDataIfAbsent(key, value);
      }
    }
    else {
      synchronized (dataHolder) {
        value = dataHolder.getUserData(key);
        if (value instanceof CachedValueImpl && !((CachedValueImpl)value).isFromMyProject(myManager.getProject())) {
          value = null;
        }
        if (value == null) {
          value = createCachedValue(provider, trackValue);
          dataHolder.putUserData(key, value);
        }
      }
    }
    return value.getValue();
  }
}
