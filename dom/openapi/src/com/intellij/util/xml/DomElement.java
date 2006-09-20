/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomElement extends AnnotatedElement{

  DomElement[] EMPTY_ARRAY = new DomElement[0];

  @Nullable
  XmlTag getXmlTag();

  @NotNull
  <T extends DomElement> DomFileElement<T> getRoot();

  @Nullable
  DomElement getParent();

  XmlTag ensureTagExists();

  @Nullable
  XmlElement getXmlElement();

  XmlElement ensureXmlElementExists();

  void undefine();

  boolean isValid();

  @NotNull
  DomGenericInfo getGenericInfo();

  @NotNull
  String getXmlElementName();

  void accept(final DomElementVisitor visitor);

  void acceptChildren(DomElementVisitor visitor);

  @NotNull
  DomManager getManager();

  Type getDomElementType();

  DomNameStrategy getNameStrategy();

  @NotNull
  ElementPresentation getPresentation();

  GlobalSearchScope getResolveScope();

  <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict);

  Module getModule();

  void copyFrom(DomElement other);

  <T extends DomElement> T createMockCopy(final boolean physical);

  <T extends DomElement> T createStableCopy();

}
