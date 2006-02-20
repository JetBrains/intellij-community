/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.DomGenericInfo;
import com.intellij.javaee.model.ElementPresentation;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomElement {

  @Nullable
  XmlTag getXmlTag();

  DomFileElement<?> getRoot();

  DomElement getParent();

  XmlTag ensureTagExists();

  void undefine();

  boolean isValid();

  DomGenericInfo getGenericInfo();

  String getXmlElementName();

  void accept(final DomElementVisitor visitor);

  void acceptChildren(DomElementVisitor visitor);

  DomManager getManager();

  Type getDomElementType();

  DomNameStrategy getNameStrategy();

  @NotNull
  ElementPresentation getPresentation();

  GlobalSearchScope getResolveScope();

  <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict);
}
