// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;

public abstract class PatternCondition<T> {
  private static final Logger LOG = Logger.getInstance(PatternCondition.class);
  private static final @NonNls String PARAMETER_FIELD_PREFIX = "val$";
  private final String myDebugMethodName;

  public PatternCondition(@Nullable @NonNls String debugMethodName) {
    myDebugMethodName = debugMethodName;
  }

  public String getDebugMethodName() {
    return myDebugMethodName;
  }

  private static void appendValue(final StringBuilder builder, final String indent, final Object obj) {
    if (obj instanceof ElementPattern) {
      ((ElementPattern)obj).getCondition().append(builder, indent + "  ");
    } else if (obj instanceof Object[]) {
      appendArray(builder, indent, (Object[])obj);
    } else if (obj instanceof Collection) {
      appendArray(builder, indent, ((Collection<?>) obj).toArray());
    }
    else if (obj instanceof String) {
      builder.append('\"').append(obj).append('\"');
    }
    else {
      builder.append(obj);
    }
  }

  protected static void appendArray(final StringBuilder builder, final String indent, final Object[] objects) {
    builder.append("[");
    boolean first = true;
    for (final Object o : objects) {
      if (!first) {
        builder.append(", ");
      }
      first = false;
      appendValue(builder, indent, o);
    }
    builder.append("]");
  }

  public abstract boolean accepts(@NotNull T t, final ProcessingContext context);

  @Override
  public @NonNls String toString() {
    final StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(StringBuilder builder, String indent) {
    builder.append(myDebugMethodName);
    builder.append("(");
    appendParams(builder, indent);
    builder.append(")");
  }

  private void appendParams(final StringBuilder builder, final String indent) {
    processParameters(new PairProcessor<String, Object>() {
      int count;
      String prevName;
      int prevOffset;

      @Override
      public boolean process(String name, Object value) {
        count ++;
        if (count == 2) builder.insert(prevOffset, prevName +"=");
        if (count > 1) builder.append(", ");
        prevOffset = builder.length();
        if (count > 1) builder.append(name).append("=");
        appendValue(builder, indent, value);
        prevName = name;
        return true;
      }
    });
  }

  // this code eats CPU, for debug purposes ONLY
  public boolean processParameters(final PairProcessor<? super String, Object> processor) {
    for (Class aClass = getClass(); aClass != null; aClass = aClass.getSuperclass()) {
      for (final Field field : aClass.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) &&
            (!field.isSynthetic() && !aClass.equals(PatternCondition.class)
             || field.getName().startsWith(PARAMETER_FIELD_PREFIX))) {
          final String name = field.getName();
          final String fixedName = name.startsWith(PARAMETER_FIELD_PREFIX) ?
                                   name.substring(PARAMETER_FIELD_PREFIX.length()) : name;
          final Object value = getFieldValue(field);
          if (!processor.process(fixedName, value)) return false;
        }
      }
    }
    return true;
  }

  private Object getFieldValue(Field field) {
    final boolean accessible = field.isAccessible();
    try {
      field.setAccessible(true);
      return field.get(this);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    finally {
      field.setAccessible(accessible);
    }
    return null;
  }

}
