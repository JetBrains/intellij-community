/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.WeakValueHashMap;
import com.intellij.util.xml.JavaMethodSignature;
import net.sf.cglib.proxy.AdvancedEnhancer;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.InvocationHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class AdvancedProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AdvancedProxy");
  public static Method FINALIZE_METHOD;

  static {
    try {
      FINALIZE_METHOD = Object.class.getDeclaredMethod("finalize");
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
  }


  private static final Map<ProxyDescription, Factory> ourFactories = new WeakValueHashMap<ProxyDescription, Factory>();

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return (InvocationHandler)((Factory) proxy).getCallback(0);
  }

  public static <T> T createProxy(final InvocationHandler handler, final Class<T> superClassOrInterface, final Class... otherInterfaces) {
    if (ReflectionCache.isInterface(superClassOrInterface)) {
      Class[] interfaces = new Class[otherInterfaces.length+1];
      interfaces [0] = superClassOrInterface;
      System.arraycopy(otherInterfaces, 0, interfaces, 1, otherInterfaces.length);
      return (T) createProxy(null, interfaces, handler, Collections.<JavaMethodSignature>emptySet());
    }
    return (T) createProxy(superClassOrInterface, otherInterfaces, handler, Collections.<JavaMethodSignature>emptySet());
  }

  public static <T> T createProxy(final Class<T> superClass,
                                  final Class[] interfaces,
                                  final InvocationHandler handler,
                                  final Set<JavaMethodSignature> additionalMethods,
                                  final Object... constructorArgs) {
    try {
      final Callback[] callbacks = new Callback[]{handler};

      final ProxyDescription key = new ProxyDescription(superClass, interfaces, additionalMethods);
      Factory factory = ourFactories.get(key);
      if (factory != null) {
        final Class<? extends Factory> aClass = factory.getClass();
        return (T) factory.newInstance(getConstructorParameterTypes(aClass, constructorArgs), constructorArgs, callbacks);
      }

      //System.out.println("key = " + key);
      AdvancedEnhancer e = new AdvancedEnhancer();
      e.setAdditionalMethods(additionalMethods);
      e.setSuperclass(superClass);
      e.setInterfaces(interfaces);
      e.setCallbacks(callbacks);
      if (superClass != null) {
        factory = (Factory)e.create(getConstructorParameterTypes(superClass, constructorArgs), constructorArgs);
      } else {
        assert constructorArgs.length == 0;
        factory = (Factory)e.create();
      }

      ourFactories.put(key, factory);
      return (T)factory;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Class[] getConstructorParameterTypes(final Class aClass, final Object... constructorArgs) {
    if (constructorArgs.length == 0) return ArrayUtil.EMPTY_CLASS_ARRAY;

    loop: for (final Constructor constructor : aClass.getDeclaredConstructors()) {
      final Class[] parameterTypes = constructor.getParameterTypes();
      if (parameterTypes.length == constructorArgs.length) {
        for (int i = 0; i < parameterTypes.length; i++) {
          Class parameterType = parameterTypes[i];
          final Object constructorArg = constructorArgs[i];
          if (!parameterType.isInstance(constructorArg) && constructorArg != null) {
            continue loop;
          }
        }
        return constructor.getParameterTypes();
      }
    }
    throw new AssertionError("Cannot find constructor for arguments: " + Arrays.asList(constructorArgs));
  }

  private static class ProxyDescription {
    private final Class mySuperClass;
    private final Class[] myInterfaces;
    private final Set<JavaMethodSignature> myAdditionalMethods;

    public ProxyDescription(final Class superClass, final Class[] interfaces, final Set<JavaMethodSignature> additionalMethods) {
      mySuperClass = superClass;
      myInterfaces = interfaces;
      myAdditionalMethods = additionalMethods;
    }

    public String toString() {
      return mySuperClass + " " + (myInterfaces != null ? Arrays.asList(myInterfaces)  + " ": "") + myAdditionalMethods;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ProxyDescription that = (ProxyDescription)o;

      if (myAdditionalMethods != null ? !myAdditionalMethods.equals(that.myAdditionalMethods) : that.myAdditionalMethods != null) return false;
      if (!Arrays.equals(myInterfaces, that.myInterfaces)) return false;
      if (mySuperClass != null ? !mySuperClass.equals(that.mySuperClass) : that.mySuperClass != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (mySuperClass != null ? mySuperClass.hashCode() : 0);
      result = 29 * result + (myAdditionalMethods != null ? myAdditionalMethods.hashCode() : 0);
      return result;
    }
  }

}
