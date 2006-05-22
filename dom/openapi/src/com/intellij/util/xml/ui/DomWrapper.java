/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public interface DomWrapper<T> {
  @Nullable
  DomElement getDomElement();
  void setValue(T value) throws IllegalAccessException, InvocationTargetException;
  T getValue() throws IllegalAccessException, InvocationTargetException;

  boolean isValid();

  Project getProject();

  GlobalSearchScope getResolveScope();
}
