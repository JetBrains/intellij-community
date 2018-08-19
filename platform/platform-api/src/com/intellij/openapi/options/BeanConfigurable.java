/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Setter;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  private abstract static class BeanField<T extends JComponent> {
    String myFieldName;
    T myComponent;

    private BeanField(final String fieldName) {
      myFieldName = fieldName;
    }

    T getComponent() {
      if (myComponent == null) {
        myComponent = createComponent();
      }
      return myComponent;
    }

    @NotNull
    abstract T createComponent();

    boolean isModified(@NotNull Object instance) {
      final Object componentValue = getComponentValue();
      final Object beanValue = getBeanValue(instance);
      return !Comparing.equal(componentValue, beanValue);
    }

    void apply(@NotNull Object instance) {
      setBeanValue(instance, getComponentValue());
    }

    void reset(@NotNull Object instance) {
      setComponentValue(getBeanValue(instance));
    }

    abstract Object getComponentValue();
    abstract void setComponentValue(Object value);

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

    @NonNls
    protected String getterName() {
      return "get" + StringUtil.capitalize(myFieldName);
    }

    void setBeanValue(Object instance, Object value) {
      try {
        Field field = instance.getClass().getField(myFieldName);
        field.set(instance, value);
      }
      catch (NoSuchFieldException e) {
        try {
          final Method method = instance.getClass().getMethod("set" + StringUtil.capitalize(myFieldName), getValueClass());
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

    protected abstract Class getValueClass();
  }

  private static class CheckboxField extends BeanField<JCheckBox> {
    private final String myTitle;

    private CheckboxField(final String fieldName, final String title) {
      super(fieldName);
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

    @Override
    protected String getterName() {
      return "is" + StringUtil.capitalize(myFieldName);
    }

    @Override
    protected Class getValueClass() {
      return boolean.class;
    }
  }

  private final List<BeanField> myFields = new ArrayList<>();

  protected BeanConfigurable(@NotNull T beanInstance) {
    myInstance = beanInstance;
  }

  @NotNull
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
    CheckboxField field = new CheckboxField("", title) {
      @Override
      Object getBeanValue(@NotNull Object instance) {
        return getter.get();
      }

      @Override
      void setBeanValue(Object instance, Object value) {
        setter.set((Boolean)value);
      }
    };
    myFields.add(field);
  }

  /**
   * Adds custom component (e.g. edit box).
   * Initial value is obtained from {@code beanGetter} and applied to the component via {@code componentSetter}.
   * E.g. text is read from the model and set to the edit box.
   * After the apply, the value from the component is queried via {@code componentGetter} and written back to model via {@code beanSetter}.
   * E.g. text from the edit box is queried and saved back to model bean.
   */
  protected <V> void component(@NotNull JComponent component, @NotNull Getter<V> beanGetter, @NotNull Setter<V> beanSetter, @NotNull Getter<V> componentGetter, @NotNull Setter<V> componentSetter) {
    BeanField<JComponent> field = new BeanField<JComponent>("") {
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

      @Override
      protected Class getValueClass() {
        return null;
      }

      @Override
      Object getBeanValue(@NotNull Object instance) {
        return beanGetter.get();
      }

      @Override
      void setBeanValue(Object instance, Object value) {
        beanSetter.set((V)value);
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
