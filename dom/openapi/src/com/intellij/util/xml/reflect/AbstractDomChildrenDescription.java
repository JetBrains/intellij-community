/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.AnnotatedElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface AbstractDomChildrenDescription extends AnnotatedElement {
  @NotNull
  List<? extends DomElement> getValues(@NotNull DomElement parent);

  @NotNull
  List<? extends DomElement> getStableValues(@NotNull DomElement parent);

  @NotNull
  Type getType();

  @NotNull
  DomNameStrategy getDomNameStrategy(@NotNull DomElement parent);
}
