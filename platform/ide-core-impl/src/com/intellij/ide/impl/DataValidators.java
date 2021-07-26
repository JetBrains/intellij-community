// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DataValidators {
  private static final Logger LOG = Logger.getInstance(DataValidators.class);

  public static final ExtensionPointName<DataValidators> EP_NAME = ExtensionPointName.create("com.intellij.dataValidators");

  public abstract void collectValidators(@NotNull Registry registry);

  interface Validator<T> {
    boolean checkValid(@NotNull T data, @NotNull String dataId, @NotNull Object source);
  }

  interface Registry {
    <T> void register(@NotNull DataKey<T> key, @NotNull Validator<? super T> validator);
  }

  public static <T> @NotNull Validator<T[]> arrayValidator(@NotNull Validator<? super T> validator) {
    return (data, dataId, source) -> {
      for (T element : data) {
        if (element == null) {
          T notNull = ContainerUtil.find(data, Conditions.notNull());
          LOG.error("Array with null provided by " + source.getClass().getName() + ".getData(\"" + dataId + "\")" +
                    ": " + data.getClass().getComponentType().getName() + "[" + data.length + "] " +
                    "{" + (notNull == null ? null : notNull.getClass().getName()) + (data.length > 1 ? ", ..." : "") + "}");
          return false;
        }
        if (!validator.checkValid(element, dataId, source)) {
          return false;
        }
      }
      return true;
    };
  }

  public static @Nullable Object validOrNull(@NotNull Object data, @NotNull String dataId, @NotNull Object source) {
    return isDataValid(data, dataId, source) ? data : null;
  }

  private static boolean isDataValid(@NotNull Object data, @NotNull String dataId, @NotNull Object source) {
    //noinspection unchecked
    Validator<Object>[] validators = (Validator<Object>[])getValidators(dataId);
    if (validators == null) return true;
    try {
      for (Validator<Object> validator : validators) {
        if (!validator.checkValid(data, dataId, source)) return false;
      }
    }
    catch (ClassCastException ex) {
      LOG.error("Object of incorrect type provided by " + source.getClass().getName() + ".getData(\"" + dataId + "\")");
      return false;
    }
    return true;
  }

  private static final Map<String, Validator<?>[]> ourValidators = new ConcurrentHashMap<>();

  static {
    EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull DataValidators extension,
                                 @NotNull PluginDescriptor pluginDescriptor) {
        ourValidators.clear();
      }

      @Override
      public void extensionRemoved(@NotNull DataValidators extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        ourValidators.clear();
      }
    }, null);
  }

  static Validator<?> @Nullable [] getValidators(@NotNull String dataId) {
    Validator<?>[] result = ourValidators.get(dataId);
    if (result != null || !ourValidators.isEmpty()) {
      return result;
    }
    Map<String, List<Validator<?>>> map = FactoryMap.create(__ -> new ArrayList<>());
    Registry registry = new Registry() {
      @Override
      public <T> void register(@NotNull DataKey<T> key,
                               @NotNull Validator<? super T> validator) {
        map.get(key.getName()).add(validator);
      }
    };
    for (DataValidators validators : EP_NAME.getExtensionList()) {
      validators.collectValidators(registry);
    }
    Validator<?>[] emptyArray = new Validator[0];
    for (String s : map.keySet()) {
      ourValidators.put(s, map.get(s).toArray(emptyArray));
    }
    return ourValidators.get(dataId);
  }
}
