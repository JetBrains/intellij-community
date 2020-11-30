// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author yole
 */
public abstract class KeyedExtensionFactory<T, KeyT> {
  private final Class<T> myInterfaceClass;
  private final ExtensionPointName<KeyedFactoryEPBean> myEpName;
  private final ComponentManager componentManager;

  public KeyedExtensionFactory(@NotNull Class<T> interfaceClass,
                               @NotNull ExtensionPointName<KeyedFactoryEPBean> epName,
                               @NotNull ComponentManager componentManager) {
    myInterfaceClass = interfaceClass;
    myEpName = epName;
    this.componentManager = componentManager;
  }

  public @NotNull T get() {
    InvocationHandler handler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) {
        List<KeyedFactoryEPBean> epBeans = myEpName.getExtensionList();
        //noinspection unchecked
        KeyT keyArg = (KeyT) args [0];
        String key = getKey(keyArg);
        Object result = getByKey(epBeans, key, method, args);
        if (result == null) {
          result = getByKey(epBeans, null, method, args);
        }
        return result;
      }
    };
    //noinspection unchecked
    return (T)Proxy.newProxyInstance(myInterfaceClass.getClassLoader(), new Class<?>[]{myInterfaceClass}, handler);
  }

  public T getByKey(@NotNull KeyT key) {
    return findByKey(getKey(key), myEpName, componentManager);
  }

  public static @Nullable <T> T findByKey(@NotNull String key,
                                          @NotNull ExtensionPointName<KeyedFactoryEPBean> point,
                                          @NotNull ComponentManager componentManager) {
    for (KeyedFactoryEPBean epBean : point.getExtensionList()) {
      if (!key.equals(epBean.key) || epBean.implementationClass == null) {
        continue;
      }
      return componentManager.instantiateClass(epBean.implementationClass, epBean.getPluginDescriptor());
    }
    return null;
  }

  private T getByKey(@NotNull List<KeyedFactoryEPBean> epBeans, @Nullable String key, @NotNull Method method, Object[] args) {
    for (KeyedFactoryEPBean epBean : epBeans) {
      if (!(epBean.key == null ? "" : epBean.key).equals(key == null ? "" : key)) {
        continue;
      }

      if (epBean.implementationClass != null) {
        return componentManager.instantiateClass(epBean.implementationClass, epBean.getPluginDescriptor());
      }

      Object factory = componentManager.instantiateClass(epBean.factoryClass, epBean.getPluginDescriptor());
      try {
        //noinspection unchecked
        T result = (T)method.invoke(factory, args);
        if (result != null) {
          return result;
        }
      }
      catch (RuntimeException e) {
        if (e instanceof ControlFlowException) {
          throw e;
        }
        throw componentManager.createError(e, epBean.getPluginDescriptor().getPluginId());
      }
      catch (Exception e) {
        throw componentManager.createError(e, epBean.getPluginDescriptor().getPluginId());
      }
    }
    return null;
  }

  public abstract @NotNull String getKey(@NotNull KeyT key);
}

