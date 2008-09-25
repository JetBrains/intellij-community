/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PhysicalDomParentStrategy implements DomParentStrategy {
  private XmlElement myElement;

  public PhysicalDomParentStrategy(@NotNull final XmlElement element) {
    myElement = element;
  }

  public DomInvocationHandler getParentHandler() {
    final XmlTag parentTag = (XmlTag)getParentTag();
    assert parentTag != null;
    return DomManagerImpl.getDomManager(myElement.getProject()).getDomHandler(parentTag);
  }

  private PsiElement getParentTag() {
    final PsiElement parent = myElement.getParent();
    return parent instanceof XmlEntityRef ? parent.getParent() : parent;
  }

  @NotNull
  public final XmlElement getXmlElement() {
    return myElement;
  }

  @NotNull
  public DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    return this;
  }

  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    myElement = element;
    return this;
  }

  @NotNull
  public DomParentStrategy clearXmlElement() {
    final DomInvocationHandler parent = getParentHandler();
    assert parent != null : "write operations should be performed on the DOM having a parent, your DOM may be not very fresh";
    return new VirtualDomParentStrategy(parent);
  }

  public boolean isValid() {
    return myElement.isValid();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof PhysicalDomParentStrategy)) return false;

    final PhysicalDomParentStrategy that = (PhysicalDomParentStrategy)o;

    if (!myElement.equals(that.myElement)) return false;

    return true;
  }

  public int hashCode() {
    return myElement.hashCode();
  }
}
