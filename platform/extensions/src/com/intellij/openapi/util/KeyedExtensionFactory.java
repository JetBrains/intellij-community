// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.util.ExceptionUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class KeyedExtensionFactory<T, KeyT> {
  private final Class<T> myInterfaceClass;
  private final ExtensionPointName<KeyedFactoryEPBean> myEpName;
  private final PicoContainer myPicoContainer;

  public KeyedExtensionFactory(@NotNull final Class<T> interfaceClass, @NonNls @NotNull final ExtensionPointName<KeyedFactoryEPBean> epName,
                               @NotNull PicoContainer picoContainer) {
    myInterfaceClass = interfaceClass;
    myEpName = epName;
    myPicoContainer = picoContainer;
  }

  @NotNull
  public T get() {
    final List<KeyedFactoryEPBean> epBeans = myEpName.getExtensionList();
    InvocationHandler handler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
    return (T)Proxy.newProxyInstance(myInterfaceClass.getClassLoader(), new Class<?>[] { myInterfaceClass }, handler );
  }

  public T getByKey(@NotNull KeyT key) {
    final List<KeyedFactoryEPBean> epBeans = myEpName.getExtensionList();
    for (KeyedFactoryEPBean epBean : epBeans) {
      if (Comparing.strEqual(getKey(key), epBean.key)) {
        try {
          if (epBean.implementationClass != null) {
            return (T)epBean.instantiate(epBean.implementationClass, myPicoContainer);
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    return null;
  }

  @NotNull
  public Set<String> getAllKeys() {
    List<KeyedFactoryEPBean> list = myEpName.getExtensionList();
    Set<String> set = new THashSet<>();
    for (KeyedFactoryEPBean epBean : list) {
      set.add(epBean.key);
    }
    return set;
  }

  private T getByKey(final List<KeyedFactoryEPBean> epBeans, final String key, final Method method, final Object[] args) {
    Object result = null;
    for(KeyedFactoryEPBean epBean: epBeans) {
      if (Comparing.strEqual(epBean.key, key, true)) {
        try {
          if (epBean.implementationClass != null) {
            result = epBean.instantiate(epBean.implementationClass, myPicoContainer);
          }
          else {
            Object factory = epBean.instantiate(epBean.factoryClass, myPicoContainer);
            result = method.invoke(factory, args);
          }
          if (result != null) {
            break;
          }
        }
        catch (InvocationTargetException e) {
          ExceptionUtil.rethrowUnchecked(e.getCause());
          throw new RuntimeException(e);
        }
        catch (RuntimeException e) {
          throw e;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    return (T)result;
  }

  public abstract String getKey(@NotNull KeyT key);
}

