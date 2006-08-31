/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.WeakArrayHashMap;
import com.intellij.util.xml.impl.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class ModelMergerImpl implements ModelMerger {
  private final WeakArrayHashMap<Object, Object> myMergedMap = new WeakArrayHashMap<Object, Object>();
  private final List<Pair<InvocationStrategy,Class>> myInvocationStrategies = new ArrayList<Pair<InvocationStrategy,Class>>();
  private final List<MergingStrategy> myMergingStrategies = new ArrayList<MergingStrategy>();
  private final List<Class> myMergingStrategyClasses = new ArrayList<Class>();
  private static final Class<MergedObject> MERGED_OBJECT_CLASS = MergedObject.class;

  private final ConcurrentFactoryMap<Method,List<Pair<InvocationStrategy,Class>>> myAcceptsCache = new ConcurrentFactoryMap<Method,List<Pair<InvocationStrategy,Class>>>() {
    protected List<Pair<InvocationStrategy,Class>> create(final Method method) {
      List<Pair<InvocationStrategy,Class>> result = new ArrayList<Pair<InvocationStrategy,Class>>();
      for (int i = myInvocationStrategies.size() - 1; i >= 0; i--) {
        final Pair<InvocationStrategy, Class> pair = myInvocationStrategies.get(i);
        if (pair.first.accepts(method)) {
          result.add(pair);
        }
      }
      return result;
    }
  };

  public ModelMergerImpl() {
    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      public boolean accepts(final Method method) {
        return true;
      }

      public Object invokeMethod(final JavaMethod javaMethod, final Object[] args, final Object[] implementations)
        throws IllegalAccessException, InvocationTargetException {
        final Method method = javaMethod.getMethod();
        List<Object> results = getMergedImplementations(method, args, method.getReturnType(), implementations);
        return results.isEmpty() ? null : results.get(0);
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      public boolean accepts(final Method method) {
        return Collection.class.isAssignableFrom(method.getReturnType());
      }

      public Object invokeMethod(final JavaMethod method, final Object[] args, final Object[] implementations)
        throws IllegalAccessException, InvocationTargetException {

        final Type type = DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType());
        assert type != null : "No generic return type in method " + method;
        return getMergedImplementations(method.getMethod(), args, DomReflectionUtil.getRawType(type), implementations);
      }
    });


    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      public boolean accepts(final Method method) {
        return Object.class.equals(method.getDeclaringClass());
      }

      public Object invokeMethod(final JavaMethod method, final Object[] args, final Object[] implementations) {
        @NonNls String methodName = method.getName();
        if ("toString".equals(methodName)) {
          return "Merger: " + Arrays.asList(implementations);
        }
        if ("hashCode".equals(methodName)) {
          return Arrays.hashCode(implementations);
        }
        if ("equals".equals(methodName)) {
          final Object arg = args[0];
          return arg != null && arg instanceof MergedObject &&
                 ((MergedObject)arg).getImplementations().equals(Arrays.asList(implementations));

        }
        return null;
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      public boolean accepts(final Method method) {
        return "isValid".equals(method.getName());
      }

      public Object invokeMethod(final JavaMethod method, final Object[] args, final Object[] implementations)
        throws IllegalAccessException, InvocationTargetException {
        for (final Object implementation : implementations) {
          if (!((Boolean)method.invoke(implementation, args))) {
            return Boolean.FALSE;
          }
        }
        return Boolean.TRUE;
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      public boolean accepts(final Method method) {
        return void.class.equals(method.getReturnType());
      }

      public Object invokeMethod(final JavaMethod method, final Object[] args, final Object[] implementations)
        throws IllegalAccessException, InvocationTargetException {
        for (final Object t : implementations) {
          method.invoke(t, args);
        }
        return null;
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      public boolean accepts(final Method method) {
        return MERGED_OBJECT_CLASS.equals(method.getDeclaringClass());
      }

      public Object invokeMethod(final JavaMethod method, final Object[] args, final Object[] implementations)
        throws IllegalAccessException, InvocationTargetException {
        assert "getImplementations".equals(method.getName());
        return Arrays.asList(implementations);
      }
    });

  }

  public final <T> void addInvocationStrategy(Class<T> aClass, InvocationStrategy<T> strategy) {
    myInvocationStrategies.add(Pair.<InvocationStrategy,Class>create(strategy, aClass));
  }

  public final <T> void addMergingStrategy(Class<T> aClass, MergingStrategy<T> strategy) {
    myMergingStrategies.add(strategy);
    myMergingStrategyClasses.add(aClass);
  }

  public <T> T mergeModels(final Class<T> aClass, final T... implementations) {
    final Object o = myMergedMap.get(implementations);
    if (o != null) {
      return (T)o;
    }
    if (implementations.length == 1) return implementations[0];
    final MergingInvocationHandler<T> handler = new MergingInvocationHandler<T>(aClass, implementations);
    return _mergeModels(aClass, handler, implementations);
  }

  public <T> T mergeModels(final Class<T> aClass, final Collection<? extends T> implementations) {
    return (T)mergeModels((Class)aClass, implementations.toArray());
  }


  private <T> T _mergeModels(final Class<? super T> aClass, final MergingInvocationHandler<T> handler, final T... implementations) {
    final Set<Class> commonClasses = getCommonClasses(implementations);
    commonClasses.add(MERGED_OBJECT_CLASS);
    commonClasses.add(aClass);
    final T t = AdvancedProxy.<T>createProxy(handler, null, commonClasses.toArray(new Class[commonClasses.size()]));
    myMergedMap.put(implementations, t);
    return t;
  }

  private static void addAllInterfaces(Class aClass, List<Class> list) {
    final Class[] interfaces = aClass.getInterfaces();
    list.addAll(Arrays.asList(interfaces));
    for (Class anInterface : interfaces) {
      addAllInterfaces(anInterface, list);
    }
  }

  private static Set<Class> getCommonClasses(final Object... implementations) {
    final HashSet<Class> set = new HashSet<Class>();
    if (implementations.length > 0) {
      final ArrayList<Class> list = new ArrayList<Class>();
      addAllInterfaces(implementations[0].getClass(), list);
      set.addAll(list);
      for (int i = 1; i < implementations.length; i++) {
        final ArrayList<Class> list1 = new ArrayList<Class>();
        addAllInterfaces(implementations[i].getClass(), list1);
        set.retainAll(list1);
      }
    }
    return set;
  }


  private static final Map<Class<? extends Object>, Method> ourPrimaryKeyMethods = new HashMap<Class<? extends Object>, Method>();

  public class MergingInvocationHandler<T> implements InvocationHandler {
    private final Class<? super T> myClass;
    private T[] myImplementations;

    public MergingInvocationHandler(final Class<T> aClass, final T... implementations) {
      this(aClass);
      setImplementations(implementations);
    }

    public MergingInvocationHandler(final Class<T> aClass) {
      myClass = aClass;
    }

    protected final void setImplementations(final T[] implementations) {
      myImplementations = implementations;
    }

    @NotNull
    private InvocationStrategy findStrategy(final Object proxy, final Method method) {
      for (final Pair<InvocationStrategy, Class> pair : myAcceptsCache.get(method)) {
        if (pair.second.isInstance(proxy)) {
          return pair.first;
        }
      }
      throw new AssertionError("impossible");
    }

    public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
      InvocationStack.INSTANCE.push(method, proxy);
      try {
        try {
          return findStrategy(proxy, method).invokeMethod(getJavaMethod(method), args, myImplementations);
        }
        catch (InvocationTargetException e) {
          throw e.getCause();
        }
      }
      finally {
        InvocationStack.INSTANCE.pop();
      }
    }

    private JavaMethod getJavaMethod(final Method method) {
      if (ReflectionCache.isAssignable(MERGED_OBJECT_CLASS, method.getDeclaringClass())) {
        return JavaMethod.getMethod(MERGED_OBJECT_CLASS, method);
      }
      if (ReflectionCache.isAssignable(method.getDeclaringClass(), myClass)) {
        return JavaMethod.getMethod(myClass, method);
      }
      return JavaMethod.getMethod(method.getDeclaringClass(), method);
    }
  }

  @Nullable
  protected static Object getPrimaryKey(Object implementation, final boolean singleValuedInvocation) throws IllegalAccessException, InvocationTargetException {
    if (implementation instanceof GenericValue) {
      return singleValuedInvocation? Boolean.TRUE : ((GenericValue)implementation).getValue();
    }
    final Method method = getPrimaryKeyMethod(implementation.getClass());
    if (method == null) return null;

    final Object o = DomReflectionUtil.invokeMethod(method, implementation);
    return ReflectionCache.isAssignable(GenericValue.class, method.getReturnType()) ? ((GenericValue)o).getValue() : o;
  }

  @Nullable
  private static Method getPrimaryKeyMethod(final Class<? extends Object> aClass) {
    Method method = ourPrimaryKeyMethods.get(aClass);
    if (method == null) {
      if (ourPrimaryKeyMethods.containsKey(aClass)) return null;

      for (final Method method1 : aClass.getMethods()) {
        if ((method = findPrimaryKeyAnnotatedMethod(method1, aClass)) != null) {
          break;
        }
      }
      ourPrimaryKeyMethods.put(aClass, method);
    }
    return method;
  }

  @Nullable
  private static Method findPrimaryKeyAnnotatedMethod(final Method method, final Class aClass) {
    return method.getReturnType() != void.class && method.getParameterTypes().length == 0 ? JavaMethodSignature.getSignature(method)
      .findAnnotatedMethod(PrimaryKey.class, aClass) : null;
  }

  private List<Object> getMergedImplementations(final Method method,
                                                final Object[] args,
                                                final Class returnType,
                                                final Object[] implementations) throws IllegalAccessException, InvocationTargetException {

    final List<Object> results = new ArrayList<Object>();

    if (ReflectionCache.isInterface(returnType)) {
      final List<Object> orderedPrimaryKeys = new SmartList<Object>();
      final FactoryMap<Object, List<Set<Object>>> map = new FactoryMap<Object, List<Set<Object>>>() {
        @NotNull
        protected List<Set<Object>> create(final Object key) {
          orderedPrimaryKeys.add(key);
          return new SmartList<Set<Object>>();
        }
      };
      final FactoryMap<Object, int[]> counts = new FactoryMap<Object, int[]>() {
        @NotNull
        protected int[] create(final Object key) {
          return new int[implementations.length];
        }
      };
      for (int i = 0; i < implementations.length; i++) {
        Object t = implementations[i];
        final Object o = method.invoke(t, args);
        if (o instanceof Collection) {
          for (final Object o1 : (Collection)o) {
            addToMaps(o1, counts, map, i, results, false);
          }
        }
        else if (o != null) {
          addToMaps(o, counts, map, i, results, true);
        }

      }

      for (final Object primaryKey : orderedPrimaryKeys) {
        for (final Set<Object> objects : map.get(primaryKey)) {
          results.add(mergeImplementations(returnType, objects.toArray()));
        }
      }
    }
    else {
      HashSet<Object> map = new HashSet<Object>();
      for (final Object t : implementations) {
        final Object o = method.invoke(t, args);
        if (o instanceof Collection) {
          map.addAll((Collection<Object>)o);
        }
        else if (o != null) {
          map.add(o);
          break;
        }
      }
      results.addAll(map);
    }
    return results;
  }

  protected final Object mergeImplementations(final Class returnType, final Object... implementations) {
    for (int i = myMergingStrategies.size() - 1; i >= 0; i--) {
      if (ReflectionCache.isAssignable(myMergingStrategyClasses.get(i), returnType)) {
        final Object o = myMergingStrategies.get(i).mergeChildren(returnType, implementations);
        if (o != null) {
          return o;
        }
      }
    }
    if (implementations.length == 1) {
      return implementations[0];
    }
    return mergeModels(returnType, implementations);
  }

  private boolean addToMaps(final Object o,
                            final FactoryMap<Object, int[]> counts,
                            final FactoryMap<Object, List<Set<Object>>> map,
                            final int index,
                            final List<Object> results,
                            final boolean singleValuedInvocation) throws IllegalAccessException, InvocationTargetException {
    final Object primaryKey = getPrimaryKey(o, singleValuedInvocation);
    if (primaryKey != null || singleValuedInvocation) {
      final List<Set<Object>> list = map.get(primaryKey);
      int objIndex = counts.get(primaryKey)[index]++;
      if (list.size() <= objIndex) {
        list.add(new LinkedHashSet<Object>());
      }
      list.get(objIndex).add(o);
      return false;
    }

    results.add(o);
    return true;
  }


}
