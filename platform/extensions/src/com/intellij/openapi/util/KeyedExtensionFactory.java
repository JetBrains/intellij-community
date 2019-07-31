// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.progress.ProcessCanceledException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
      public Object invoke(Object proxy, Method method, Object[] args) {
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
    return findByKey(getKey(key), myEpName, myPicoContainer);
  }

  @Nullable
  public static <T> T findByKey(@NotNull String key, @NotNull ExtensionPointName<KeyedFactoryEPBean> point, @NotNull PicoContainer picoContainer) {
    for (KeyedFactoryEPBean epBean : point.getExtensionList()) {
      if (!key.equals(epBean.key) || epBean.implementationClass == null) {
        continue;
      }

      try {
        return (T)epBean.instantiateClass(epBean.implementationClass, picoContainer);
      }
      catch (ProcessCanceledException | ExtensionInstantiationException e) {
        throw e;
      }
      catch (Exception e) {
        throw new ExtensionInstantiationException(e, epBean.getPluginDescriptor());
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

  private T getByKey(final List<? extends KeyedFactoryEPBean> epBeans, final String key, final Method method, final Object[] args) {
    Object result = null;
    for(KeyedFactoryEPBean epBean: epBeans) {
      if (Comparing.strEqual(epBean.key, key, true)) {
        try {
          if (epBean.implementationClass != null) {
            result = epBean.instantiateClass(epBean.implementationClass, myPicoContainer);
          }
          else {
            Object factory = epBean.instantiateClass(epBean.factoryClass, myPicoContainer);
            result = method.invoke(factory, args);
          }
          if (result != null) {
            break;
          }
        }
        catch (InvocationTargetException e) {
          Throwable t = e.getCause();
          if (t instanceof ControlFlowException && t instanceof RuntimeException) throw (RuntimeException)t;
          throw new ExtensionInstantiationException(e, epBean.getPluginDescriptor());
        }
        catch (ExtensionInstantiationException e) {
          throw e;
        }
        catch (RuntimeException e) {
          if (e instanceof ControlFlowException) {
            throw e;
          }
          throw new ExtensionInstantiationException(e, epBean.getPluginDescriptor());
        }
        catch (Exception e) {
          throw new ExtensionInstantiationException(e, epBean.getPluginDescriptor());
        }
      }
    }
    return (T)result;
  }

  @NotNull
  public abstract String getKey(@NotNull KeyT key);
}

