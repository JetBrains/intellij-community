// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DataValidators {
  private static final Logger LOG = Logger.getInstance(DataValidators.class);

  public static final ExtensionPointName<DataValidators> EP_NAME = ExtensionPointName.create("com.intellij.dataValidators");

  public abstract void collectValidators(@NotNull Registry registry);

  public interface Validator<T> {
    boolean checkValid(@NotNull T data, @NotNull String dataId, @NotNull Object source);
  }

  public interface Registry {
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
    if (isPsiElementProvided(data) &&
        EDT.isCurrentThreadEdt() &&
        SlowOperations.isInSection(SlowOperations.FORCE_ASSERT)) {
      reportPsiElementOnEdt(dataId, source);
    }
    //noinspection unchecked
    Validator<Object>[] validators = (Validator<Object>[])getValidators(dataId);
    if (validators == null) return true;
    try {
      for (Validator<Object> validator : validators) {
        if (!validator.checkValid(data, dataId, source)) return false;
      }
    }
    catch (ClassCastException ex) {
      reportObjectOfIncorrectType(dataId, source, ex);
      return false;
    }
    return true;
  }

  private static boolean isPsiElementProvided(@Nullable Object data) {
    if (data instanceof PsiElement && !(data instanceof FakePsiElement)) return true;
    if (data instanceof Object[] array) return isPsiElementProvided(ArrayUtil.getFirstElement(array));
    if (data instanceof Collection<?> collection) return isPsiElementProvided(ContainerUtil.getFirstItem(collection));
    return false;
  }

  private static void reportPsiElementOnEdt(@NotNull String dataId, @NotNull Object source) {
    LOG.error(PluginException.createByClass(
      "PSI element is provided on EDT by " + source.getClass().getName() + ".getData(\"" + dataId + "\"). " +
      "Please move that to a BGT data provider using PlatformCoreDataKeys.BGT_DATA_PROVIDER", null, source.getClass()));
  }

  private static void reportObjectOfIncorrectType(@NotNull String dataId, @NotNull Object source, @NotNull ClassCastException ex) {
    LOG.error(PluginException.createByClass(
      "Object of incorrect type provided by " + source.getClass().getName() +
      ".getData(\"" + dataId + "\")", ex, source.getClass()));
  }

  private static final Map<String, Validator<?>[]> ourValidators = new ConcurrentHashMap<>();

  static {
    Application app = ApplicationManager.getApplication();
    ExtensionPoint<@NotNull KeyedLazyInstance<GetDataRule>> dataRuleEP = app.getExtensionArea()
      .getExtensionPointIfRegistered(EP_NAME.getName());
    if (dataRuleEP != null) {
      dataRuleEP.addChangeListener(() -> ourValidators.clear(), app);
    }
  }

  static Validator<?> @Nullable [] getValidators(@NotNull String dataId) {
    Validator<?>[] result = ourValidators.get(dataId);
    if (result != null || !ourValidators.isEmpty()) {
      return result;
    }
    List<DataValidators> extensions = EP_NAME.getExtensionsIfPointIsRegistered();
    if (extensions.isEmpty()) {
      return null;
    }
    Map<String, List<Validator<?>>> map = FactoryMap.create(__ -> new ArrayList<>());
    Registry registry = new Registry() {
      @Override
      public <T> void register(@NotNull DataKey<T> key,
                               @NotNull Validator<? super T> validator) {
        map.get(key.getName()).add(validator);
      }
    };
    for (DataValidators validators : extensions) {
      validators.collectValidators(registry);
    }
    Validator<?>[] emptyArray = new Validator[0];
    for (String s : map.keySet()) {
      ourValidators.put(s, map.get(s).toArray(emptyArray));
    }
    return ourValidators.get(dataId);
  }
}
