/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.openapi.util.Key;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.ConvertAnnotationImpl;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class DomExtensionImpl implements DomExtension {
  public static final Key<List<DomExtender>> DOM_EXTENDER_KEY = Key.create("Dom.Extender");
  private final XmlName myXmlName;
  private final Type myType;
  private Converter myConverter;
  private String myExtendClass;
  private boolean mySoft;
  private int myCount = 1;
  private Map myUserMap;

  public DomExtensionImpl(final Type type, final XmlName xmlName) {
    myType = type;
    myXmlName = xmlName;
  }

  @NotNull
  public XmlName getXmlName() {
    return myXmlName;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public DomExtension setConverter(@NotNull Converter converter) {
    return setConverter(converter, false);
  }

  public final DomExtension setConverter(@NotNull final Converter converter, final boolean soft) {
    myConverter = converter;
    mySoft = soft;
    return this;
  }

  public DomExtension setExtendClass(@NotNull @NonNls final String className) {
    myExtendClass = className;
    return this;
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    if (myUserMap == null) myUserMap = new THashMap();
    myUserMap.put(key, value);
  }

  public DomExtension addExtender(final DomExtender extender) {
    if (myUserMap == null || !myUserMap.containsKey(DOM_EXTENDER_KEY)) {
      putUserData(DOM_EXTENDER_KEY, new SmartList<DomExtender>());
    }
    ((List<DomExtender>)myUserMap.get(DOM_EXTENDER_KEY)).add(extender);
    return this;
  }

  public final DomExtensionImpl setCount(final int count) {
    myCount = count;
    return this;
  }

  public final int getCount() {
    return myCount;
  }

  public final <T extends DomChildDescriptionImpl> T addAnnotations(T t) {
    t.setUserMap(myUserMap);
    if (myConverter != null) {
      t.addCustomAnnotation(new ConvertAnnotationImpl(myConverter, mySoft));
    }
    if (myExtendClass != null) {
      t.addCustomAnnotation(new ExtendClass() {


        public boolean instantiatable() {
          return false;
        }

        public boolean canBeDecorator() {
          return false;
        }

        public boolean allowEmpty() {
          return false;
        }

        public boolean allowAbstract() {
          return true;
        }

        public boolean allowInterface() {
          return true;
        }

        public String value() {
          return myExtendClass;
        }

        public Class<? extends Annotation> annotationType() {
          return ExtendClass.class;
        }
      });
    }
    return t;
  }
}
