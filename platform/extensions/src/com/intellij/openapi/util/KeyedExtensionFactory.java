// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author yole
 */
public abstract class KeyedExtensionFactory<T, KeyT> {
  private final Class<T> myInterfaceClass;
  private final ExtensionPointName<KeyedFactoryEPBean> myEpName;
  private final PicoContainer myPicoContainer;

  public KeyedExtensionFactory(final @NotNull Class<T> interfaceClass, @NonNls final @NotNull ExtensionPointName<KeyedFactoryEPBean> epName,
                               @NotNull PicoContainer picoContainer) {
    myInterfaceClass = interfaceClass;
    myEpName = epName;
    myPicoContainer = picoContainer;
  }

  public @NotNull T get() {
    InvocationHandler handler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) {
        final List<KeyedFactoryEPBean> epBeans = myEpName.getExtensionList();
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

  public static @Nullable <T> T findByKey(@NotNull String key, @NotNull ExtensionPointName<KeyedFactoryEPBean> point, @NotNull PicoContainer picoContainer) {
    for (KeyedFactoryEPBean epBean : point.getExtensionList()) {
      if (!key.equals(epBean.key) || epBean.implementationClass == null) {
        continue;
      }

      try {
        return epBean.instantiateClass(epBean.implementationClass, picoContainer);
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

  private T getByKey(final List<? extends KeyedFactoryEPBean> epBeans, final String key, final Method method, final Object[] args) {
    T result = null;
    for(KeyedFactoryEPBean epBean: epBeans) {
      if (Comparing.strEqual(epBean.key, key, true)) {
        try {
          if (epBean.implementationClass != null) {
            result = epBean.instantiateClass(epBean.implementationClass, myPicoContainer);
          }
          else {
            Object factory = epBean.instantiateClass(epBean.factoryClass, myPicoContainer);
            //noinspection unchecked
            result = (T)method.invoke(factory, args);
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
    return result;
  }

  public abstract @NotNull String getKey(@NotNull KeyT key);
}

