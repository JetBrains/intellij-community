/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class VirtualDomParentStrategy implements DomParentStrategy {
  private final DomInvocationHandler myParentHandler;
  private long myModCount;

  public VirtualDomParentStrategy(final DomInvocationHandler parentHandler) {
    myParentHandler = parentHandler;
    myModCount = getModCount();
  }

  private long getModCount() {
    return PsiManager.getInstance(myParentHandler.getManager().getProject()).getModificationTracker().getOutOfCodeBlockModificationCount();
  }

  @NotNull
  public DomInvocationHandler getParentHandler() {
    return myParentHandler;
  }

  public XmlElement getXmlElement() {
    return null;
  }

  @NotNull
  public synchronized DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    if (!myParentHandler.isValid()) return this;

    final long modCount = getModCount();
    if (modCount != myModCount) {
      final XmlElement xmlElement = handler.recomputeXmlElement(myParentHandler);
      if (xmlElement != null) {
        return new PhysicalDomParentStrategy(xmlElement);
      }
      myModCount = modCount;
    }
    return this;
  }

  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    return new PhysicalDomParentStrategy(element);
  }

  @NotNull
  public synchronized DomParentStrategy clearXmlElement() {
    myModCount = getModCount();
    return this;
  }

  public synchronized boolean isValid() {
    return getModCount() == myModCount;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof VirtualDomParentStrategy)) return false;

    final VirtualDomParentStrategy that = (VirtualDomParentStrategy)o;

    if (!myParentHandler.equals(that.myParentHandler)) return false;

    return true;
  }

  public int hashCode() {
    return myParentHandler.hashCode();
  }
}
