package com.intellij.execution.rmi;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import gnu.trove.THashMap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.util.Map;

/**
   * @author Gregory.Shrago
   */
  public class RemoteUtil {
    RemoteUtil() {
    }

    private static final ConcurrentFactoryMap<Pair<Class<?>, Class<?>>, Map<Method, Method>> ourRemoteToLocalMap = new ConcurrentFactoryMap<Pair<Class<?>,Class<?>>, Map<Method, Method>>() {
      @Override
      protected Map<Method, Method> create(Pair<Class<?>, Class<?>> key) {
        final THashMap<Method, Method> map = new THashMap<Method, Method>();
        for (Method method : key.second.getMethods()) {
          Method m = null;
          main:
          for (Method candidate : key.first.getMethods()) {
            if (!candidate.getName().equals(method.getName())) continue;
            Class<?>[] cpts = candidate.getParameterTypes();
            Class<?>[] mpts = method.getParameterTypes();
            if (cpts.length != mpts.length) continue;
            for (int i = 0; i < mpts.length; i++) {
              Class<?> mpt = mpts[i];
              Class<?> cpt = cpts[i];
              if (!cpt.isAssignableFrom(mpt)) continue main;
            }
            m = candidate;
            break;
          }
          if (m != null) map.put(method, m);
        }
        return map;
      }
    };

    public static <T> T castToLocal(final Object remote, final Class<T> jdbcClass) {
      final ClassLoader loader = jdbcClass.getClassLoader();
      Object proxy = Proxy.newProxyInstance(loader, new Class[]{jdbcClass}, new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if (method.getDeclaringClass() == Object.class) {
            return method.invoke(remote, args);
          }
          else {
            Method m = ourRemoteToLocalMap.get(Pair.<Class<?>, Class<?>>create(remote.getClass(), jdbcClass)).get(method);
            if (m == null) throw new NoSuchMethodError(method.getName() + " in " + remote.getClass());
            try {
              Object result = m.invoke(remote, args);
              if (result instanceof Remote) {
                return castToLocal(result, tryFixReturnType(result, method.getReturnType(), loader));
              }
              return result;
            }
            catch (InvocationTargetException e) {
              Throwable cause = e;
              for (; cause.getCause() != null; cause = cause.getCause());
              if (cause instanceof RuntimeException) throw cause;
              if (ArrayUtil.indexOf(method.getExceptionTypes(), cause.getClass()) > -1) throw cause;
              throw new RuntimeException(cause);
            }
            catch (RuntimeException e) {
              throw e;
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        }
      });
      return (T)proxy;
    }

  private static Class<?> tryFixReturnType(Object result, Class<?> returnType, ClassLoader loader) throws Exception {
    if (returnType.isInterface()) return returnType;
    if (result instanceof RemoteCastable) {
      final String className = ((RemoteCastable)result).getCastToClassName();
      return Class.forName(className, true, loader);
    }
    return returnType;
  }

  public static <T> T substituteClassLoader(final T remote, final ClassLoader classLoader) throws Exception {
    return executeWithClassLoader(new ThrowableComputable<T, Exception>() {
      public T compute() {
        Object proxy = Proxy.newProxyInstance(classLoader, remote.getClass().getInterfaces(), new InvocationHandler() {
          public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
            return executeWithClassLoader(new ThrowableComputable<Object, Exception>() {
              public Object compute() throws Exception {
                try {
                  final Object result = method.invoke(remote, args);
                  if (result instanceof Remote) {
                    if (result instanceof RemoteCastable) {
                      return castToLocal(result, tryFixReturnType(result, method.getReturnType(), classLoader));
                    }
                    return substituteClassLoader(result, classLoader);
                  }
                  return result;
                }
                catch (InvocationTargetException e) {
                  Throwable cause = e;
                  while (cause.getCause() != null) cause = cause.getCause();
                  if (cause instanceof RuntimeException) throw (RuntimeException)cause;
                  if (cause instanceof Exception && ArrayUtil.indexOf(method.getExceptionTypes(), cause.getClass()) > -1) throw (Exception)cause;
                  throw new RuntimeException(cause);
                }
                catch (RuntimeException e) {
                  throw e;
                }
                catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            }, classLoader);
          }
        });
        return (T)proxy;
      }
    }, classLoader);
  }

  public static <T> T executeWithClassLoader(final ThrowableComputable<T, Exception> action, final ClassLoader classLoader) throws Exception {
    final Thread thread = Thread.currentThread();
    final ClassLoader prev = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(classLoader);
      return action.compute();
    }
    finally {
      thread.setContextClassLoader(prev);
    }
  }
}
