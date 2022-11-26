// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import com.intellij.uiDesigner.compiler.Utils;

import javax.swing.*;
import java.awt.*;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class CompiledClassPropertiesProvider implements PropertiesProvider {
  private final ClassLoader myLoader;
  private final HashMap<String, Map<String, LwIntrospectedProperty>> myCache;

  public CompiledClassPropertiesProvider(final ClassLoader loader) {
    if (loader == null) {
      throw new IllegalArgumentException("loader cannot be null");
    }
    myLoader = loader;
    myCache = new HashMap<>();
  }

  @Override
  public HashMap getLwProperties(final String className) {
    if (myCache.containsKey(className)) {
      return (HashMap)myCache.get(className);
    }

    if (Utils.validateJComponentClass(myLoader, className, false) != null) {
      return null;
    }

    final Class aClass;
    try {
      aClass = Class.forName(className, false, myLoader);
    }
    catch (final ClassNotFoundException exc) {
      throw new RuntimeException(exc.toString()); // should never happen
    }

    final BeanInfo beanInfo;
    try {
      beanInfo = Introspector.getBeanInfo(aClass);
    }
    catch (Throwable e) {
      return null;
    }

    final HashMap<String, LwIntrospectedProperty> result = new HashMap<>();
    final PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
    for (final PropertyDescriptor descriptor : descriptors) {
      final Method readMethod = descriptor.getReadMethod();
      final Method writeMethod = descriptor.getWriteMethod();
      final Class propertyType = descriptor.getPropertyType();
      if (writeMethod == null || readMethod == null || propertyType == null) {
        continue;
      }

      final String name = descriptor.getName();

      final LwIntrospectedProperty property = propertyFromClass(propertyType, name);

      if (property != null) {
        property.setDeclaringClassName(descriptor.getReadMethod().getDeclaringClass().getName());
        result.put(name, property);
      }
    }

    myCache.put(className, result);

    return result;
  }

  static LwIntrospectedProperty propertyFromClass(final Class<?> propertyType, final String name) {
    LwIntrospectedProperty property = propertyFromClassName(propertyType.getName(), name);
    if (property == null) {
      if (Component.class.isAssignableFrom(propertyType)) {
        property = new LwIntroComponentProperty(name, propertyType.getName());
      }
      else if (ListModel.class.isAssignableFrom(propertyType)) {
        property = new LwIntroListModelProperty(name, propertyType.getName());
      }
      else if (propertyType.getSuperclass() != null && "java.lang.Enum".equals(propertyType.getSuperclass().getName())) {
        property = new LwIntroEnumProperty(name, propertyType);
      }
    }
    return property;
  }

  public static LwIntrospectedProperty propertyFromClassName(final String propertyClassName, final String name) {
    final LwIntrospectedProperty property;
    if (int.class.getName().equals(propertyClassName)) { // int
      property = new LwIntroIntProperty(name);
    }
    else if (boolean.class.getName().equals(propertyClassName)) { // boolean
      property = new LwIntroBooleanProperty(name);
    }
    else if (double.class.getName().equals(propertyClassName)) { // double
      property = new LwIntroPrimitiveTypeProperty(name, Double.class);
    }
    else if (float.class.getName().equals(propertyClassName)) {
      property = new LwIntroPrimitiveTypeProperty(name, Float.class);
    }
    else if (long.class.getName().equals(propertyClassName)) {
      property = new LwIntroPrimitiveTypeProperty(name, Long.class);
    }
    else if (byte.class.getName().equals(propertyClassName)) {
      property = new LwIntroPrimitiveTypeProperty(name, Byte.class);
    }
    else if (short.class.getName().equals(propertyClassName)) {
      property = new LwIntroPrimitiveTypeProperty(name, Short.class);
    }
    else if (char.class.getName().equals(propertyClassName)) {
      property = new LwIntroCharProperty(name);
    }
    else if (String.class.getName().equals(propertyClassName)) { // java.lang.String
      property = new LwRbIntroStringProperty(name);
    }
    else if ("java.awt.Insets".equals(propertyClassName)) { // java.awt.Insets
      property = new LwIntroInsetsProperty(name);
    }
    else if ("java.awt.Dimension".equals(propertyClassName)) { // java.awt.Dimension
      property = new LwIntroDimensionProperty(name);
    }
    else if ("java.awt.Rectangle".equals(propertyClassName)) { // java.awt.Rectangle
      property = new LwIntroRectangleProperty(name);
    }
    else if ("java.awt.Color".equals(propertyClassName)) {
      property = new LwIntroColorProperty(name);
    }
    else if ("java.awt.Font".equals(propertyClassName)) {
      property = new LwIntroFontProperty(name);
    }
    else if ("javax.swing.Icon".equals(propertyClassName)) {
      property = new LwIntroIconProperty(name);
    }
    else {
      property = null;
    }
    return property;
  }
}
