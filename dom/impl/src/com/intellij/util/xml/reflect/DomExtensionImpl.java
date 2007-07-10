/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import com.intellij.util.xml.impl.ConvertAnnotationImpl;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class DomExtensionImpl implements DomExtension {
  private final XmlName myXmlName;
  private final Type myType;
  private Converter myConverter;
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
    return t;
  }
}
