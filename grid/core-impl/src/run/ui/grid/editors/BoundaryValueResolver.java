package com.intellij.database.run.ui.grid.editors;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Map;


public interface BoundaryValueResolver {
  BoundaryValueResolver ALWAYS_NULL = new BoundaryValueResolver() {
    @Override
    public @NotNull Object createJdbcNegativeInfinityValue() {
      throw new UnsupportedOperationException("Should never happens");
    }

    @Override
    public @NotNull Object createJdbcPositiveInfinityValue() {
      throw new UnsupportedOperationException("Should never happens");
    }

    @Override
    public @NotNull java.util.Date getPresentablePositiveInfinity() {
      throw new UnsupportedOperationException("Should never happens");
    }

    @Override
    public @NotNull java.util.Date getPresentableNegativeInfinity() {
      throw new UnsupportedOperationException("Should never happens");
    }

    @Override
    public boolean isPositiveInfinity(@NotNull Object object) {
      return false;
    }

    @Override
    public boolean isNegativeInfinity(@NotNull Object object) {
      return false;
    }

    @Override
    public @Nullable String getPositiveInfinityString() {
      return null;
    }

    @Override
    public @Nullable String getNegativeInfinityString() {
      return null;
    }

    @Override
    public @Nullable Class<?> getObjectClass() {
      return null;
    }
  };

  @NotNull java.util.Date getPresentablePositiveInfinity();

  @NotNull java.util.Date getPresentableNegativeInfinity();

  @NotNull Object createJdbcPositiveInfinityValue();

  @NotNull Object createJdbcNegativeInfinityValue();

  boolean isPositiveInfinity(@NotNull Object object);

  boolean isNegativeInfinity(@NotNull Object object);

  @Nullable String getPositiveInfinityString();

  @Nullable String getNegativeInfinityString();

  @Nullable Class<?> getObjectClass();

  default @Nullable String resolve(@NotNull Object value) {
    Class<?> objectClass = getObjectClass();
    return objectClass != null && objectClass.isInstance(value) ? getInfinityString(value) : null;
  }

  default @Nullable String getInfinityString(@Nullable Object value) {
    return value != null && isPositiveInfinity(value) ? getPositiveInfinityString() :
           value != null && isNegativeInfinity(value) ? getNegativeInfinityString() :
           null;
  }

  default @NotNull java.util.Date bound(@NotNull Object object) {
    return isPositiveInfinity(object) ? getPresentablePositiveInfinity() :
           isNegativeInfinity(object) ? getPresentableNegativeInfinity() :
           getLegacyDate(object);
  }

  default @Nullable Object createFromInfinityString(@NotNull String value) {
    return StringUtil.equalsIgnoreWhitespaces(value, getPositiveInfinityString()) ? createJdbcPositiveInfinityValue() :
           StringUtil.equalsIgnoreWhitespaces(value, getNegativeInfinityString()) ? createJdbcNegativeInfinityValue() :
           null;
  }

  default @NotNull java.util.Date getLegacyDate(@NotNull Object object) {
    return Rules.MAP.get(object.getClass()).fun(object);
  }

  final class Rules {
    static final Map<Class<?>, Function<Object, java.util.Date>> MAP =
      Map.of(
        java.util.Date.class, o -> (java.util.Date)o,
        java.sql.Date.class, o -> (java.util.Date)o,
        Timestamp.class, o -> (java.util.Date)o,
        Time.class, o -> (java.util.Date)o);
  }
}
