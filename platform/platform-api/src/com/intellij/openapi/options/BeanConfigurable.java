// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Setter;
import com.intellij.openapi.util.text.StringUtil;
import kotlin.reflect.KMutableProperty0;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class BeanConfigurable<T> implements UnnamedConfigurable {
  private final T myInstance;

  private abstract static class BeanPropertyAccessor {
    abstract Object getBeanValue(Object instance);
    abstract void setBeanValue(Object instance, @NotNull Object value);
  }

  private static class BeanFieldAccessor extends BeanPropertyAccessor {
    private final String myFieldName;
    private final Class myValueClass;

    private BeanFieldAccessor(String fieldName, Class valueClass) {
      myFieldName = fieldName;
      myValueClass = valueClass;
    }

    @NonNls
    protected String getterName() {
      if (myValueClass.equals(boolean.class)) {
        return "is" + StringUtil.capitalize(myFieldName);
      }
      return "get" + StringUtil.capitalize(myFieldName);
    }

    @Override
    Object getBeanValue(@NotNull Object instance) {
      try {
        Field field = instance.getClass().getField(myFieldName);
        return field.get(instance);
      }
      catch (NoSuchFieldException e) {
        try {
          final Method method = instance.getClass().getMethod(getterName());
          return method.invoke(instance);
        }
        catch (Exception e1) {
          throw new RuntimeException(e1);
        }
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    void setBeanValue(@NotNull Object instance, @NotNull Object value) {
      try {
        Field field = instance.getClass().getField(myFieldName);
        field.set(instance, value);
      }
      catch (NoSuchFieldException e) {
        try {
          final Method method = instance.getClass().getMethod("set" + StringUtil.capitalize(myFieldName), myValueClass);
          method.invoke(instance, value);
        }
        catch (Exception e1) {
          throw new RuntimeException(e1);
        }
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class BeanMethodAccessor<T> extends BeanPropertyAccessor {
    private final Getter<? extends T> myGetter;
    private final Setter<? super T> mySetter;

    private BeanMethodAccessor(Getter<? extends T> getter, Setter<? super T> setter) {
      myGetter = getter;
      mySetter = setter;
    }

    @Override
    Object getBeanValue(Object instance) {
      return myGetter.get();
    }

    @Override
    void setBeanValue(Object instance, @NotNull Object value) {
      //noinspection unchecked
      mySetter.set((T) value);
    }
  }

  private static class BeanKPropertyAccessor<T> extends BeanPropertyAccessor {
    private final KMutableProperty0<T> myProperty;

    private BeanKPropertyAccessor(KMutableProperty0<T> property) {
      myProperty = property;
    }

    @Override
    Object getBeanValue(Object instance) {
      return myProperty.get();
    }

    @Override
    void setBeanValue(Object instance, @NotNull Object value) {
      //noinspection unchecked
      myProperty.set((T)value);
    }
  }

  private abstract static class BeanField<T extends JComponent> {
    BeanPropertyAccessor myAccessor;
    T myComponent;

    private BeanField(final BeanPropertyAccessor accessor) {
      myAccessor = accessor;
    }

    T getComponent() {
      if (myComponent == null) {
        myComponent = createComponent();
      }
      return myComponent;
    }

    @NotNull
    abstract T createComponent();

    boolean isModified(Object instance) {
      final Object componentValue = getComponentValue();
      final Object beanValue = myAccessor.getBeanValue(instance);
      return !Comparing.equal(componentValue, beanValue);
    }

    void apply(Object instance) {
      myAccessor.setBeanValue(instance, getComponentValue());
    }

    void reset(Object instance) {
      setComponentValue(myAccessor.getBeanValue(instance));
    }

    abstract Object getComponentValue();
    abstract void setComponentValue(Object value);
  }

  private static class CheckboxField extends BeanField<JCheckBox> {
    private final String myTitle;

    private CheckboxField(final String fieldName, final String title) {
      super(new BeanFieldAccessor(fieldName, boolean.class));
      myTitle = title;
    }

    private CheckboxField(BeanPropertyAccessor accessor, String title) {
      super(accessor);
      myTitle = title;
    }

    @NotNull
    @Override
    JCheckBox createComponent() {
      return new JCheckBox(myTitle);
    }

    @Override
    Object getComponentValue() {
      return getComponent().isSelected();
    }

    @Override
    void setComponentValue(final Object value) {
      getComponent().setSelected(((Boolean) value).booleanValue());
    }
  }

  private final List<BeanField> myFields = new ArrayList<>();

  protected BeanConfigurable(@NotNull T beanInstance) {
    myInstance = beanInstance;
  }

  protected BeanConfigurable() {
    myInstance = null;
  }

  @Nullable
  protected T getInstance() {
    return myInstance;
  }

  /**
   * @deprecated use {@link #checkBox(String, Getter, Setter)} instead
   */
  @Deprecated
  protected void checkBox(@NonNls String fieldName, String title) {
    myFields.add(new CheckboxField(fieldName, title));
  }

  /**
   * Adds check box with given {@code title}.
   * Initial checkbox value is obtained from {@code getter}.
   * After the apply, the value from the check box is written back to model via {@code setter}.
   */
  protected void checkBox(@NotNull String title, @NotNull Getter<Boolean> getter, @NotNull Setter<Boolean> setter) {
    CheckboxField field = new CheckboxField(new BeanMethodAccessor<>(getter, setter), title);
    myFields.add(field);
  }

  protected void checkBox(@NotNull String title, @NotNull KMutableProperty0<Boolean> prop) {
    myFields.add(new CheckboxField(new BeanKPropertyAccessor<>(prop), title));
  }

  /**
   * Adds custom component (e.g. edit box).
   * Initial value is obtained from {@code beanGetter} and applied to the component via {@code componentSetter}.
   * E.g. text is read from the model and set to the edit box.
   * After the apply, the value from the component is queried via {@code componentGetter} and written back to model via {@code beanSetter}.
   * E.g. text from the edit box is queried and saved back to model bean.
   */
  protected <V> void component(@NotNull JComponent component, @NotNull Getter<? extends V> beanGetter, @NotNull Setter<? super V> beanSetter, @NotNull Getter<? extends V> componentGetter, @NotNull Setter<? super V> componentSetter) {
    BeanField<JComponent> field = new BeanField<JComponent>(new BeanMethodAccessor<V>(beanGetter, beanSetter)) {
      @NotNull
      @Override
      JComponent createComponent() {
        return component;
      }

      @Override
      Object getComponentValue() {
        return componentGetter.get();
      }

      @Override
      void setComponentValue(Object value) {
        componentSetter.set((V)value);
      }
    };
    myFields.add(field);
  }

  @Override
  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridLayout(myFields.size(), 1));
    for (BeanField field: myFields) {
      panel.add(field.getComponent());
    }
    return panel;
  }

  @Override
  public boolean isModified() {
    for (BeanField field : myFields) {
      if (field.isModified(myInstance)) return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (BeanField field : myFields) {
      field.apply(myInstance);
    }
  }

  @Override
  public void reset() {
    for (BeanField field : myFields) {
      field.reset(myInstance);
    }
  }
}
