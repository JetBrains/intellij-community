package com.intellij.openapi.options;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

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
  private T myInstance;

  private static abstract class BeanField<T extends JComponent> {
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

    abstract T createComponent();

    boolean isModified(Object instance) {
      final Object componentValue = getComponentValue();
      final Object beanValue = getBeanValue(instance);
      return !Comparing.equal(componentValue, beanValue);
    }

    void apply(Object instance) {
      setBeanValue(instance, getComponentValue());
    }

    void reset(Object instance) {
      setComponentValue(getBeanValue(instance));
    }

    abstract Object getComponentValue();
    abstract void setComponentValue(Object instance);

    Object getBeanValue(Object instance) {
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
          final Method method = instance.getClass().getMethod("set" + StringUtil.capitalize(myFieldName));
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

  private static class CheckboxField extends BeanField<JCheckBox> {
    private String myTitle;

    private CheckboxField(final String fieldName, final String title) {
      super(fieldName);
      myTitle = title;
    }

    JCheckBox createComponent() {
      return new JCheckBox(myTitle);
    }

    Object getComponentValue() {
      return getComponent().isSelected();
    }

    void setComponentValue(final Object instance) {
      getComponent().setSelected(((Boolean) instance).booleanValue());
    }

    @Override
    protected String getterName() {
      return "is" + StringUtil.capitalize(myFieldName);
    }
  }

  private List<BeanField> myFields = new ArrayList<BeanField>();

  protected BeanConfigurable(T beanInstance) {
    myInstance = beanInstance;
  }

  protected void checkBox(@NonNls String fieldName, String title) {
    myFields.add(new CheckboxField(fieldName, title));
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridLayout(myFields.size(), 1));
    for (BeanField field: myFields) {
      panel.add(field.getComponent());
    }
    return panel;
  }

  public boolean isModified() {
    for (BeanField field : myFields) {
      if (field.isModified(myInstance)) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (BeanField field : myFields) {
      field.apply(myInstance);
    }
  }

  public void reset() {
    for (BeanField field : myFields) {
      field.reset(myInstance);
    }
  }

  public void disposeUIResources() {
  }
}
