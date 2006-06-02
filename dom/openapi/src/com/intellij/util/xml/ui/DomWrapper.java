/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public abstract class DomWrapper<T> {

  @NotNull
  public abstract DomElement getExistingDomElement();

  @Nullable
  public abstract DomElement getWrappedElement();

  public abstract void setValue(T value) throws IllegalAccessException, InvocationTargetException;
  public abstract T getValue() throws IllegalAccessException, InvocationTargetException;

  public boolean isValid() {
    return getExistingDomElement().isValid();
  }

  public Project getProject() {
    return getExistingDomElement().getManager().getProject();
  }

  public GlobalSearchScope getResolveScope() {
    return getExistingDomElement().getResolveScope();
  }

  public XmlFile getFile() {
    return getExistingDomElement().getRoot().getFile();
  }
}
