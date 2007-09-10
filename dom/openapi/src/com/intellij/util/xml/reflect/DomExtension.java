/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomExtension {
  @NotNull
  XmlName getXmlName();

  @NotNull
  Type getType();

  DomExtension setConverter(@NotNull Converter converter);

  DomExtension setConverter(@NotNull Converter converter, boolean soft);

  DomExtension setExtendClass(@NotNull @NonNls String className);

}
