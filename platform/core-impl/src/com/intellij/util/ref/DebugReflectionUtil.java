// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ref;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.RefValueHashMapUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;

public final class DebugReflectionUtil {
  private static final Map<Class<?>, Field[]> allFields =
    Collections.synchronizedMap(CollectionFactory.createCustomHashingStrategyMap(new HashingStrategy<Class<?>>() {
      // default strategy seems to be too slow
      @Override
      public int hashCode(@Nullable Class<?> aClass) {
        return aClass == null ? 0 : aClass.getName().hashCode();
      }

      @Override
      public boolean equals(@Nullable Class<?> o1, @Nullable Class<?> o2) {
        return o1 == o2;
      }
    }));

  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
  private static final Method Unsafe_shouldBeInitialized;

  static {
    Method shouldBeInitialized;
    try {
      shouldBeInitialized = ReflectionUtil.getDeclaredMethod(Class.forName("sun.misc.Unsafe"), "shouldBeInitialized", Class.class);
    }
    catch (ClassNotFoundException ignored) {
      shouldBeInitialized = null;
    }
    Unsafe_shouldBeInitialized = shouldBeInitialized;
  }

  private static Field @NotNull [] getAllFields(@NotNull Class<?> aClass) {
    Field[] cached = allFields.get(aClass);
    if (cached == null) {
      try {
        Field[] declaredFields = aClass.getDeclaredFields();
        List<Field> fields = new ArrayList<>(declaredFields.length + 5);
        for (Field declaredField : declaredFields) {
          declaredField.setAccessible(true);
          Class<?> type = declaredField.getType();
          if (isTrivial(type)) continue; // unable to hold references, skip
          fields.add(declaredField);
        }
        Class<?> superclass = aClass.getSuperclass();
        if (superclass != null) {
          for (Field sup : getAllFields(superclass)) {
            if (!fields.contains(sup)) {
              fields.add(sup);
            }
          }
        }
        cached = fields.isEmpty() ? EMPTY_FIELD_ARRAY : fields.toArray(new Field[0]);
      }
      catch (IncompatibleClassChangeError | NoClassDefFoundError | SecurityException e) {
        // this exception may be thrown because there are two different versions of org.objectweb.asm.tree.ClassNode from different plugins
        //I don't see any sane way to fix it until we load all the plugins by the same classloader in tests
        cached = EMPTY_FIELD_ARRAY;
      }
      catch (@ReviseWhenPortedToJDK("9") RuntimeException e) {
        // field.setAccessible() can now throw this exception when accessing unexported module
        if (e.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
          cached = EMPTY_FIELD_ARRAY;
        }
        else {
          throw e;
        }
      }

      allFields.put(aClass, cached);
    }
    return cached;
  }

  private static boolean isTrivial(@NotNull Class<?> type) {
    return type.isPrimitive() || type == String.class || type == Class.class || type.isArray() && isTrivial(type.getComponentType());
  }

  private static boolean isInitialized(@NotNull Class<?> root) {
    if (Unsafe_shouldBeInitialized == null) return false;
    boolean isInitialized = false;
    try {
      isInitialized = !(Boolean)Unsafe_shouldBeInitialized.invoke(ReflectionUtil.getUnsafe(), root);
    }
    catch (Exception e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    return isInitialized;
  }

  private static final Key<Boolean> REPORTED_LEAKED = Key.create("REPORTED_LEAKED");

  public static <V> boolean walkObjects(int maxDepth,
                                        @NotNull Map<Object, String> startRoots,
                                        @NotNull Class<V> lookFor,
                                        @NotNull Predicate<Object> shouldExamineValue,
                                        @NotNull PairProcessor<? super V, ? super BackLink<?>> leakProcessor) {
    return walkObjects(maxDepth, Integer.MAX_VALUE, startRoots, lookFor, shouldExamineValue, leakProcessor);
  }
  public static <V> boolean walkObjects(int maxDepth,
                                        int maxQueueSize,
                                        @NotNull Map<Object, String> startRoots,
                                        @NotNull Class<V> lookFor,
                                        @NotNull Predicate<Object> shouldExamineValue,
                                        @NotNull PairProcessor<? super V, ? super BackLink<?>> leakProcessor) {
    IntSet visited = new IntOpenHashSet(1000);
    Deque<BackLink<?>> toVisit = new ArrayDeque<>(1000);

    for (Map.Entry<Object, String> entry : startRoots.entrySet()) {
      Object startRoot = entry.getKey();
      String description = entry.getValue();
      toVisit.addLast(new BackLink<Object>(startRoot, null, "(root)", null) {
        @Override
        void print(@NotNull StringBuilder result) {
          super.print(result);
          result.append(" (from ").append(description).append(")");
        }
      });
    }

    while (true) {
      BackLink<?> backLink = toVisit.pollFirst();
      if (backLink == null) {
        return true;
      }

      if (backLink.depth > maxDepth) {
        continue;
      }
      Object value = backLink.value;
      if (lookFor.isAssignableFrom(value.getClass()) && markLeaked(value) && !leakProcessor.process((V)value, backLink)) {
        return false;
      }

      if (visited.add(System.identityHashCode(value))) {
        queueStronglyReferencedValues(toVisit, maxQueueSize, value, backLink, shouldExamineValue);
      }
    }
  }

  private static void queueStronglyReferencedValues(@NotNull Deque<? super BackLink<?>> queue,
                                                    int maxQueueSize,
                                                    @NotNull Object root,
                                                    @NotNull BackLink<?> backLink,
                                                    @NotNull Predicate<Object> shouldExamineValue) {
    Class<?> rootClass = root.getClass();
    if (root instanceof Map) {
      RefValueHashMapUtil.expungeStaleEntries((Map<?, ?>)root);
    }
    for (Field field : getAllFields(rootClass)) {
      String fieldName = field.getName();
      // do not follow weak/soft refs
      if (root instanceof Reference && ("referent".equals(fieldName) || "discovered".equals(fieldName))) {
        continue;
      }

      Object value;
      try {
        value = field.get(root);
      }
      catch (IllegalArgumentException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }

      queue(value, field, null, backLink, queue, maxQueueSize, shouldExamineValue);
    }
    if (rootClass.isArray()) {
      try {
        Object[] objects = (Object[])root;
        for (int i = 0; i < objects.length; i++) {
          Object value = objects[i];
          queue(value, null, "["+i+"]", backLink, queue, maxQueueSize, shouldExamineValue);
        }
      }
      catch (ClassCastException ignored) {
      }
    }
    // check for objects leaking via static fields. process initialized classes only
    if (root instanceof Class && isInitialized((Class<?>)root)) {
        for (Field field : getAllFields((Class<?>)root)) {
          if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
          try {
            Object value = field.get(null);
            queue(value, field, null, backLink, queue, maxQueueSize, shouldExamineValue);
          }
          catch (IllegalAccessException ignored) {
          }
        }
    }
  }

  private static void queue(@Nullable Object value,
                            @Nullable Field field,
                            @Nullable String fieldName,
                            @NotNull BackLink<?> backLink,
                            @NotNull Deque<? super BackLink<?>> queue,
                            int maxQueueSize,
                            @NotNull Predicate<Object> shouldExamineValue) {
    if (value == null || isTrivial(value.getClass())) {
      return;
    }
    if (shouldExamineValue.test(value) && queue.size() < maxQueueSize) {
      queue.addLast(new BackLink<>(value, field, fieldName, backLink));
    }
  }

  private static boolean markLeaked(Object leaked) {
    return !(leaked instanceof UserDataHolderEx) || ((UserDataHolderEx)leaked).replace(REPORTED_LEAKED, null, Boolean.TRUE);
  }

  public static class BackLink<V> {
    private final @NotNull V value;
    private final Field field;
    /**
     * human-readable field name (sometimes the Field is not available, e.g., when it's synthetic).
     * when null, it can be computed from field.getName()
     */
    private final String fieldName;
    private final BackLink<?> backLink;
    private final int depth;

    BackLink(@NotNull V value, @Nullable Field field, @Nullable String fieldName, @Nullable BackLink<?> backLink) {
      this.value = value;
      this.field = field;
      this.fieldName = fieldName;
      assert field != null ^ fieldName != null : "One of field/fieldName must be null and the other not-null, but got: "+field+"/"+fieldName;
      this.backLink = backLink;
      depth = backLink == null ? 0 : backLink.depth + 1;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      BackLink<?> backLink = this;
      while (backLink != null) {
        backLink.print(result);
        backLink = backLink.prev();
      }
      return result.toString();
    }

    BackLink<?> prev() {
      return backLink;
    }

    String getFieldName() {
      return this.fieldName != null ? this.fieldName : field.getDeclaringClass().getName() + "." + field.getName();
    }

    void print(@NotNull StringBuilder result) {
      String valueStr;
      Object value = this.value;
      try {
        if (value instanceof FList) {
          valueStr = "FList (size=" + ((FList<?>)value).size() + ")";
        }
        else {
          valueStr = value instanceof Collection ? "Collection (size=" + ((Collection<?>)value).size() + ")"
          : value instanceof Object[] ? Arrays.toString((Object[])value)
          : String.valueOf(value);
        }
        valueStr = StringUtil.first(StringUtil.convertLineSeparators(valueStr, "\\n"), 200, true);
      }
      catch (Throwable e) {
        valueStr = "(" + e.getMessage() + " while computing .toString())";
      }

      result.append("via '").append(getFieldName()).append("'; Value: '").append(valueStr).append("' of ").append(value.getClass())
        .append("\n");
    }
  }
}
