/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import com.intellij.util.xml.impl.ConvertAnnotationImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Type;
import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public class DomExtensionImpl implements DomExtension {
  private final XmlName myXmlName;
  private final Type myType;
  private Converter myConverter;
  private String myExtendClass;
  private boolean mySoft;
  private int myCount = 1;

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

  public final DomExtensionImpl setCount(final int count) {
    myCount = count;
    return this;
  }

  public final int getCount() {
    return myCount;
  }

  public final <T extends DomChildDescriptionImpl> T addAnnotations(T t) {
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
