/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.util.xml.XmlName;
import com.intellij.psi.xml.XmlElement;

/**
 * @author peter
 */
public class DummyEvaluatedXmlName implements EvaluatedXmlName{
  private final XmlName myXmlName;
  private final String myNamespace;

  public DummyEvaluatedXmlName(final String localName, final String namespace) {
    myXmlName = new XmlName(localName);
    myNamespace = namespace;
  }

  public XmlName getXmlName() {
    return myXmlName;
  }

  public EvaluatedXmlName evaluateChildName(@NotNull final XmlName name) {
    String namespaceKey = name.getNamespaceKey();
    if (namespaceKey == null) {
      return new DummyEvaluatedXmlName(name.getLocalName(), myNamespace);
    }
    return new EvaluatedXmlNameImpl(name, namespaceKey);
  }

  public boolean isNamespaceAllowed(final DomInvocationHandler handler, final String namespace) {
    return namespace.equals(myNamespace);
  }

  @NotNull
  @NonNls
  public String getNamespace(@NotNull final XmlElement parentElement) {
    return myNamespace;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DummyEvaluatedXmlName that = (DummyEvaluatedXmlName)o;

    if (myNamespace != null ? !myNamespace.equals(that.myNamespace) : that.myNamespace != null) return false;
    if (myXmlName != null ? !myXmlName.equals(that.myXmlName) : that.myXmlName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myXmlName != null ? myXmlName.hashCode() : 0);
    result = 31 * result + (myNamespace != null ? myNamespace.hashCode() : 0);
    return result;
  }
}
