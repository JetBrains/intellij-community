/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.ui.UserActivityWatcher;

import javax.swing.table.TableCellEditor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class DomUIFactory implements ApplicationComponent {
  public static Method GET_VALUE_METHOD = null;
  public static Method SET_VALUE_METHOD = null;
  public static Method GET_STRING_METHOD = null;
  public static Method SET_STRING_METHOD = null;

  static {
    try {
      GET_VALUE_METHOD = GenericDomValue.class.getMethod("getValue");
      GET_STRING_METHOD = GenericDomValue.class.getMethod("getStringValue");
      SET_VALUE_METHOD = findMethod(GenericDomValue.class, "setValue");
      SET_STRING_METHOD = findMethod(GenericDomValue.class, "setStringValue");
    }
    catch (NoSuchMethodException e) {
      Logger.getInstance("#com.intellij.util.xml.ui.DomUIFactory").error(e);
    }
  }


  public static DomUIControl<GenericDomValue> createControl(GenericDomValue element) {
    return createControl(element, false);
  }

  public static DomUIControl<GenericDomValue> createControl(GenericDomValue element, boolean commitOnEveryChange) {
    return createGenericValueControl(DomUtil.getGenericValueType(element.getDomElementType()), element, commitOnEveryChange);
  }

  public static DomUIControl createSmallDescriptionControl(DomElement parent, final boolean commitOnEveryChange) {
    return createLargeDescriptionControl(parent, commitOnEveryChange);
  }

  public static DomUIControl createLargeDescriptionControl(DomElement parent, final boolean commitOnEveryChange) {
    return getDomUIFactory().createTextControl(new DomCollectionWrapper<String>(parent, parent.getGenericInfo().getCollectionChildDescription("description")), commitOnEveryChange);
  }

  private static BaseControl createGenericValueControl(final Type type, final GenericDomValue element, boolean commitOnEveryChange) {
    final DomStringWrapper stringWrapper = new DomStringWrapper(element);
    final Class rawType = DomUtil.getRawType(type);
    if (PsiClass.class.isAssignableFrom(rawType)) {
      return getDomUIFactory().createPsiClassControl(stringWrapper, commitOnEveryChange);
    }
    if (type.equals(PsiType.class)) {
      return getDomUIFactory().createPsiTypeControl(stringWrapper, commitOnEveryChange);
    }
    if (type instanceof Class && Enum.class.isAssignableFrom(rawType)) {
      return new ComboControl(stringWrapper, rawType);
    }
    if (DomElement.class.isAssignableFrom(rawType)) {
      return new ComboControl(element);
    }

    final DomFixedWrapper wrapper = new DomFixedWrapper(element);
    if (type.equals(boolean.class) || type.equals(Boolean.class)) {
      return new BooleanControl(wrapper);
    }
    if (type.equals(String.class)) {
      return getDomUIFactory().createTextControl(wrapper, commitOnEveryChange);
    }

    final BaseControl customControl = getDomUIFactory().createCustomControl(type, new DomStringWrapper(element), commitOnEveryChange);
    if (customControl != null) return customControl;

    throw new IllegalArgumentException("Not supported: " + type);
  }

  public static Method findMethod(Class clazz, String methodName) {
    final Method[] methods = clazz.getMethods();
    for (Method method : methods) {
      if (methodName.equals(method.getName())) {
        return method;
      }
    }
    return null;
  }

  protected abstract TableCellEditor createCellEditor(DomElement element, Class type);

  public abstract UserActivityWatcher createEditorAwareUserActivityWatcher();

  public abstract BaseControl createPsiClassControl(DomWrapper<String> wrapper, final boolean commitOnEveryChange);

  public abstract BaseControl createPsiTypeControl(DomWrapper<String> wrapper, final boolean commitOnEveryChange);

  public abstract BaseControl createTextControl(DomWrapper<String> wrapper, final boolean commitOnEveryChange);

  public abstract BaseControl createCustomControl(final Type type, DomWrapper<String> wrapper, final boolean commitOnEveryChange);

  public static BaseControl createTextControl(GenericDomValue value, final boolean commitOnEveryChange) {
    return getDomUIFactory().createTextControl(new DomStringWrapper(value), commitOnEveryChange);
  }

  public static BaseControl createTextControl(DomWrapper<String> wrapper) {
    return getDomUIFactory().createTextControl(wrapper, false);
  }

  public static DomUIFactory getDomUIFactory() {
    return ApplicationManager.getApplication().getComponent(DomUIFactory.class);
  }

  public DomUIControl createCollectionControl(DomElement element, DomCollectionChildDescription description) {
    final ColumnInfo columnInfo = createColumnInfo(description, element);
    final Class aClass = DomUtil.extractParameterClassFromGenericType(description.getType());
    return new DomCollectionControl<GenericDomValue<?>>(element, description, aClass == null, columnInfo);
  }

  public ColumnInfo createColumnInfo(final DomCollectionChildDescription description,
                                     final DomElement element) {
    final String presentableName = description.getCommonPresentableName(element);
    final Class aClass = DomUtil.extractParameterClassFromGenericType(description.getType());
    if (aClass != null) {
      if (Boolean.class.equals(aClass) || boolean.class.equals(aClass)) {
        return new BooleanColumnInfo(presentableName);
      }

      return new GenericValueColumnInfo(presentableName, aClass, createCellEditor(element, aClass));
    }

    return new StringColumnInfo(presentableName);
  }
}
