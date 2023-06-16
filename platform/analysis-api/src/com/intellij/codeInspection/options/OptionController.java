// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.util.ReflectionUtil;
import kotlin.reflect.KMutableProperty0;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An interface to get and set inspection options. {@link InspectionProfileEntry#getOptionController()}
 * returns its instance. 
 */
public interface OptionController {
  /**
   * Updates the inspection option with given ID.
   *
   * @param bindId ID of inspection option; identifier of some control inside the {@link OptPane}
   *               returned by {@link InspectionProfileEntry#getOptionsPane()} call
   * @param value  new value for the option
   * @throws IllegalArgumentException if bindId is unknown
   * @see #getOption(String)
   * @see InspectionProfileEntry#getOptionController()
   */
  void setOption(@NotNull String bindId, Object value);

  /**
   * Fetches the inspection option with given ID.
   *
   * @param bindId ID of inspection option; identifier of some control inside the {@link OptPane}
   *               returned by {@link InspectionProfileEntry#getOptionsPane()} call
   * @return inspection option with a given ID
   * @throws IllegalArgumentException if bindId is unknown
   * @see #setOption(String, Object)
   * @see InspectionProfileEntry#getOptionController()
   */
  @Nullable
  Object getOption(@NotNull String bindId);

  /**
   * @param bindId bindId of the option value to process especially; setting of this option is not supported
   *               (can be used e.g. with {@link OptStringList} control where setter is unnecessary)
   * @param getter getter for an option with a given bindId
   * @return a new controller that processes the specified bindId with a specified getter,
   * and delegates to this controller for any other bindId.
   */
  @Contract(pure = true)
  default @NotNull OptionController onValue(@NotNull String bindId, @NotNull Supplier<@NotNull Object> getter) {
    return onValue(bindId, getter, (value) -> {
      if (value != getter.get()) {
        throw new UnsupportedOperationException("Setting value is not supported");
      }
    });
  }

  /**
   * @param bindId bindId of the option value to process especially
   * @param property a Kotlin property to bind to
   * @return a new controller that binds the specified bindId to a specified Kotlin property,
   * and delegates to this controller for any other bindId.
   */
  @Contract(pure = true)
  default @NotNull OptionController onValue(@NotNull String bindId, @NotNull KMutableProperty0<?> property) {
    @SuppressWarnings("unchecked") 
    KMutableProperty0<Object> unchecked = (KMutableProperty0<Object>)property;
    return onValue(bindId, property::get, unchecked::set);
  }

  /**
   * @param <T> type of property
   * @param bindId bindId of the option value to process especially
   * @param getter getter for an option with a given bindId
   * @param setter setter for an option with a given bindId
   * @return a new controller that processes the specified bindId with a specified getter and setter,
   * and delegates to this controller for any other bindId.
   */
  @Contract(pure = true)
  default <T> @NotNull OptionController onValue(@NotNull String bindId, @NotNull Supplier<@NotNull T> getter,
                                                @NotNull Consumer<@NotNull T> setter) {
    OptionController controller = this;
    return new OptionController() {
      @Override
      public void setOption(@NotNull String _bindId, Object value) {
        if (_bindId.equals(bindId)) {
          //noinspection unchecked
          setter.accept((T)value);
        }
        else {
          controller.setOption(_bindId, value);
        }
      }

      @Override
      public Object getOption(@NotNull String _bindId) {
        if (_bindId.equals(bindId)) {
          return getter.get();
        }
        return controller.getOption(_bindId);
      }
    };
  }

  /**
   * @param bindId bindId to intercept
   * @param consumer additional action performed when setting an option with supplied bindId
   * @return a controller that behaves like this one but also performs an additional action when setting an option with supplied bindId
   */
  @Contract(pure = true)
  default @NotNull OptionController onValueSet(@NotNull String bindId, @NotNull Consumer<Object> consumer) {
    OptionController delegate = this;
    return new OptionController() {
      @Override
      public void setOption(@NotNull String _bindId, Object value) {
        delegate.setOption(_bindId, value);
        if (_bindId.equals(bindId)) {
          consumer.accept(value);
        }
      }

      @Override
      public Object getOption(@NotNull String bindId) {
        return delegate.getOption(bindId);
      }
    };
  }

  /**
   * @param consumer additional action performed when setting any option
   * @return a controller that behaves like this one but also performs an additional action when setting any option
   */
  @Contract(pure = true)
  default @NotNull OptionController onValueSet(@NotNull BiConsumer<@NotNull String, Object> consumer) {
    OptionController delegate = this;
    return new OptionController() {
      @Override
      public void setOption(@NotNull String bindId, Object value) {
        delegate.setOption(bindId, value);
        consumer.accept(bindId, value);
      }

      @Override
      public Object getOption(@NotNull String bindId) {
        return delegate.getOption(bindId);
      }
    };
  }

  /**
   * @param prefix bindId prefix of the option values to process especially
   * @param getter getter for options having a given prefix; it receives bindId with a prefix already stripped
   * @param setter setter for options having a given prefix; it receives bindId with a prefix already stripped
   * @return a new controller that processes the options having specified prefix with a specified getter and setter,
   * and delegates to this controller for any other bindId.
   */
  @Contract(pure = true)
  default @NotNull OptionController onPrefix(@NotNull String prefix,
                                             @NotNull Function<@NotNull String, Object> getter,
                                             @NotNull BiConsumer<@NotNull String, Object> setter) {
    return onPrefix(prefix, of(getter, setter));
  }

  /**
   * @param prefix bindId prefix of the option values to process especially
   * @param prefixController controller for the options having a given prefix; it receives bindId with a prefix already stripped
   * @return a new controller that processes the options having specified prefix with a specified controller,
   * and delegates to this controller for any other bindId.
   */
  @Contract(pure = true)
  default @NotNull OptionController onPrefix(@NotNull String prefix, @NotNull OptionController prefixController) {
    String fullPrefix = prefix + ".";
    OptionController controller = this;
    return new OptionController() {
      @Override
      public void setOption(@NotNull String bindId, Object value) {
        if (bindId.startsWith(fullPrefix)) {
          prefixController.setOption(bindId.substring(fullPrefix.length()), value);
        }
        else {
          controller.setOption(bindId, value);
        }
      }

      @Override
      public Object getOption(@NotNull String bindId) {
        if (bindId.startsWith(fullPrefix)) {
          return prefixController.getOption(bindId.substring(fullPrefix.length()));
        }
        return controller.getOption(bindId);
      }
    };
  }

  /**
   * @param getter getter function
   * @param setter setter function
   * @return a new controller that encapsulates specified getter and setter
   */
  @Contract(pure = true)
  static @NotNull OptionController of(@NotNull Function<@NotNull String, Object> getter, @NotNull BiConsumer<@NotNull String, Object> setter) {
    return new OptionController() {
      @Override
      public void setOption(@NotNull String bindId, Object value) {
        setter.accept(bindId, value);
      }

      @Override
      public Object getOption(@NotNull String bindId) {
        return getter.apply(bindId);
      }
    };
  }

  /**
   * @return an empty controller that cannot get or set any option. Can be used as an initial value to build more complex controller
   * using subsequent {@code onPrefix}, {@code onValue}, etc. calls.
   */
  @Contract(pure = true)
  static @NotNull OptionController empty() {
    return new OptionController() {
      @Override
      public void setOption(@NotNull String bindId, Object value) {
        throw new IllegalArgumentException(bindId);
      }

      @Override
      public Object getOption(@NotNull String bindId) {
        throw new IllegalArgumentException(bindId);
      }
    };
  }

  /**
   * @param obj object to bind to
   * @return an OptionController, which reads and writes obj instance fields whose names are the same as bindId
   */
  @Contract(pure = true)
  static @NotNull OptionController fieldsOf(@NotNull Object obj) {
    return of(
      bindId -> {
        int dot = bindId.indexOf('.');
        Field field = getField(obj, bindId, dot);
        Object value = ReflectionUtil.getFieldValue(field, obj);
        if (dot >= 0) {
          if (!(value instanceof OptionContainer container)) {
            throw new IllegalArgumentException(obj.getClass().getName() + ": Field " + field.getName() +
                                               " (" + (value == null ? null : value.getClass()) + ")" +
                                               " does not implement OptionContainer (bindId = " + bindId + ")");
          }
          try {
            return container.getOptionController().getOption(bindId.substring(dot + 1));
          }
          catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(obj.getClass().getName() + ": Field " + field.getName() + ": unable to query nested option",
                                               e);
          }
        }
        return value;
      },
      (bindId, value) -> {
        int dot = bindId.indexOf('.');
        Field field = getField(obj, bindId, dot);
        Object curValue = ReflectionUtil.getFieldValue(field, obj);
        if (dot >= 0) {
          if (!(curValue instanceof OptionContainer container)) {
            throw new IllegalArgumentException(obj.getClass().getName() + ": Field " + field.getName() +
                                               " (" + (curValue == null ? null : curValue.getClass()) + ")" +
                                               " does not implement OptionContainer (bindId = " + bindId + ")");
          }
          try {
            container.getOptionController().setOption(bindId.substring(dot + 1), value);
          }
          catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(obj.getClass().getName() + ": Field " + field.getName() + ": unable to update nested option",
                                               e);
          }
        }
        // Avoid updating field if new value is not the same
        // this way we can support final mutable fields, used by e.g. OptSet 
        else if (curValue != value) {
          try {
            field.set(obj, value);
          }
          catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
              "Inspection " + obj.getClass().getName() + ": Unable to assign field " + field.getName() + " (bindId = " + bindId + ")", e);
          }
        }
      }
    );
  }

  @NotNull
  private static Field getField(@NotNull Object obj, String bindId, int dot) {
    String fieldName = dot >= 0 ? bindId.substring(0, dot) : bindId;
    Field field;
    try {
      field = ReflectionUtil.findAssignableField(obj.getClass(), null, fieldName);
    }
    catch (NoSuchFieldException e) {
      throw new IllegalArgumentException(
        "Inspection " + obj.getClass().getName() + ": Unable to find field " + fieldName + " (bindId = " + bindId + ")", e);
    }
    return field;
  }
}
