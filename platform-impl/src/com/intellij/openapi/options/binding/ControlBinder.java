/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.openapi.options.binding;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

/**
 * @author Dmitry Avdeev
 */
public class ControlBinder {

  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.options.binding.ControlBinder");

  private final Object myBean;
  private final List<Pair<ValueAccessor, BeanValueAccessor>> myBindings = new ArrayList<Pair<ValueAccessor, BeanValueAccessor>>();

  public ControlBinder(Object bean) {
    myBean = bean;
  }

  public void bindControl(JComponent control, String propertyName) {    
    final ValueAccessor controlAccessor;
    if (control instanceof JCheckBox) {
      controlAccessor = ValueAccessor.checkBoxAccessor((JCheckBox)control);
    }
    else if (control instanceof JTextField){
      controlAccessor = ValueAccessor.textFieldAccessor((JTextField)control);
    } else {
      throw new IllegalArgumentException("Cannot bind control of type " + control.getClass() + ".\n" +
                                         "Use bindControl(ValueAccessor, String) instead.");
    }
    bindControl(controlAccessor, propertyName);
  }

  public void bindControl(ValueAccessor controlAccessor, String propertyName) {
    BeanValueAccessor beanAccessor = BeanValueAccessor.createAccessor(myBean, propertyName);
    myBindings.add(Pair.create(controlAccessor, beanAccessor));
  }

  public void reset() {
    for (Pair<ValueAccessor, BeanValueAccessor> binding : myBindings) {
      Object value = binding.second.getValue();
      try {
        value = convert(value, binding.first.getType());
        binding.first.setValue(value);
      }
      catch (IllegalArgumentException e) {
        LOG.debug(e);
      }
    }
  }

  public void apply() {
    for (Pair<ValueAccessor, BeanValueAccessor> binding : myBindings) {
      Object value = binding.first.getValue();
      try {
        value = convert(value, binding.second.getType());
        binding.second.setValue(value);
      }
      catch (IllegalArgumentException e) {
        LOG.debug(e);
      }
    }
  }

  public boolean isModified() {
    for (Pair<ValueAccessor, BeanValueAccessor> binding : myBindings) {
      Object value = binding.first.getValue();
      try {
        value = convert(value, binding.second.getType());
        if (!value.equals(binding.second.getValue())) {
          return true;
        }
      }
      catch (IllegalArgumentException e) {
        LOG.debug(e);
      }
    }
    return false;
  }

  protected Object convert(Object value, Class to) {
    Class from = value.getClass();
    if (!to.isAssignableFrom(from)) {
      if (to.isPrimitive()) {
        if (to.equals(Integer.TYPE)) {
          to = Integer.class;
        }
      }
      for (ValueTypeConverter converter : ValueTypeConverter.STANDARD) {
        if (from.equals(converter.getSourceType()) && to.equals(converter.getTargetType())) {
          return converter.from(value);
        }
        if (to.equals(converter.getSourceType()) && from.equals(converter.getTargetType())) {
          return converter.to(value);
        }
      }
    }
    return value;
  }

  public void bindAnnotations(Object form) {
    Field[] fields = form.getClass().getDeclaredFields();
    for (Field field : fields) {
      BindControl annotation = field.getAnnotation(BindControl.class);
      if (annotation != null) {
        String name = annotation.value();
        if (name.length() == 0) {
          name = field.getName();
        }
        try {
          field.setAccessible(true);
          bindControl((JComponent)field.get(form), name);
        }
        catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
