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


  public static DomUIControl createControl(GenericDomValue element) {
    return createControl(element, false);
  }

  public static DomUIControl createControl(GenericDomValue element, boolean commitOnEveryChange) {
    return createGenericValueControl(DomUtil.getGenericValueType(element.getDomElementType()), element, commitOnEveryChange);
  }

  public static DomUIControl createSmallDescriptionControl(DomElement parent, final boolean commitOnEveryChange) {
    return new BigStringControl(new DomCollectionWrapper<String>(parent, parent.getGenericInfo().getCollectionChildDescription("description")), commitOnEveryChange);
  }

  public static DomUIControl createLargeDescriptionControl(DomElement parent, final boolean commitOnEveryChange) {
    return new StringControl(new DomCollectionWrapper<String>(parent, parent.getGenericInfo().getCollectionChildDescription("description")), commitOnEveryChange);
  }

  private static BaseControl createGenericValueControl(final Type type, final GenericDomValue element, boolean commitOnEveryChange) {
    final DomStringWrapper stringWrapper = new DomStringWrapper(element);
    if (PsiClass.class.isAssignableFrom(DomUtil.getRawType(type))) {
      return getDomUIFactory().createPsiClassControl(stringWrapper, commitOnEveryChange);
    }
    if (type.equals(PsiType.class)) {
      return getDomUIFactory().createPsiTypeControl(stringWrapper, commitOnEveryChange);
    }
    if (type instanceof Class && Enum.class.isAssignableFrom((Class)type)) {
      return new ComboControl(stringWrapper, (Class)type);
    }

    final DomFixedWrapper wrapper = new DomFixedWrapper(element);
    if (type.equals(boolean.class) || type.equals(Boolean.class)) {
      return new BooleanControl(wrapper);
    }
    if (type.equals(String.class)) {
      return getDomUIFactory().createTextControl(wrapper, commitOnEveryChange);
    }

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

  public abstract BaseControl createBigTextControl(DomWrapper<String> wrapper, final boolean commitOnEveryChange);

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
