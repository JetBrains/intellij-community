/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.xml.reflect.DomGenericInfo;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Type;

/**
 * Base interface for DOM elements. Every DOM interface should extend this one.
 *
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

  /**
   * @return XmlFile, XmlTag or XmlAttribute
   */
  @Nullable
  XmlElement getXmlElement();

  XmlElement ensureXmlElementExists();

  /**
   * Removes all corresponding XML content. In case of being collection member, invalidates the element.
   */
  void undefine();

  boolean isValid();

  @NotNull
  DomGenericInfo getGenericInfo();

  @NotNull @NonNls String getXmlElementName();

  @NotNull @NonNls String getXmlElementNamespace();

  /**
   * @return namespace key if this element or one of its ancestors is annotated with
   * {@link @Namespace}, or null otherwise, which means that namespace should be equal
   * to that of the element's parent
   */
  @Nullable @NonNls String getXmlElementNamespaceKey();

  void accept(final DomElementVisitor visitor);

  void acceptChildren(DomElementVisitor visitor);

  @NotNull
  DomManager getManager();

  @NotNull
  Type getDomElementType();

  @NotNull
  DomNameStrategy getNameStrategy();

  @NotNull
  ElementPresentation getPresentation();

  GlobalSearchScope getResolveScope();

  /**
   * Walk up the DOM tree searching for element of requiredClass type
   * @param requiredClass
   * @param strict
   * strict = false: if the current element is already of the correct type, then it is returned.
   * strict = true: the returned element must be higher in the hierarchy. 
   * @return the parent of requiredClass type
   */
  @Nullable
  <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict);

  @Nullable
  Module getModule();

  void copyFrom(DomElement other);

  <T extends DomElement> T createMockCopy(final boolean physical);

  /**
   * @return stable element (see {@link DomManager#createStableValue(com.intellij.openapi.util.Factory)}}),
   * that holds the complete 'XPath' to this element in XML. If this element is in collection, and something
   * is inserted before it, the stable copy behaviour may be unexpected. So use this method only when you
   * are sure that nothing serious will happen during the lifetime of the stable copy. The most usual use
   * case is when one creates something inside {@link com.intellij.openapi.command.WriteCommandAction} and
   * wants to use it outside the action. Due to formatting done on the command finish the element may become
   * invalidated, but the stable copy will survive, because nothing in fact has changed in its 'XPath'.
   */
  <T extends DomElement> T createStableCopy();

}
