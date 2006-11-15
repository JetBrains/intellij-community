/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
*/
public class EvaluatedXmlName {
  private final XmlName myXmlName;
  private final String myNamespaceKey;

  protected EvaluatedXmlName(@NotNull final XmlName xmlName, @Nullable final String namespaceKey) {
    myXmlName = xmlName;
    myNamespaceKey = namespaceKey;
  }

  @NotNull
  public final String getLocalName() {
    return myXmlName.getLocalName();
  }

  public final XmlName getXmlName() {
    return myXmlName;
  }

  @Nullable
  public final String getNamespaceKey() {
    return myNamespaceKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EvaluatedXmlName xmlName = (EvaluatedXmlName)o;

    if (!myXmlName.equals(xmlName.myXmlName)) return false;
    if (myNamespaceKey != null ? !myNamespaceKey.equals(xmlName.myNamespaceKey) : xmlName.myNamespaceKey != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myXmlName.hashCode();
    result = 31 * result + (myNamespaceKey != null ? myNamespaceKey.hashCode() : 0);
    return result;
  }

  public final boolean isNamespaceAllowed(DomFileElementImpl element, String namespace) {
    if (myNamespaceKey == null) return true;
    final XmlFile file = element.getFile();
    return isNamespaceAllowed(namespace, getAllowedNamespaces(file));
  }

  private List<String> getAllowedNamespaces(final XmlFile file) {
    final DomFileDescription<?> description = DomManagerImpl.getDomManager(file.getProject()).getDomFileDescription(file);
    assert description != null;
    return description.getAllowedNamespaces(myNamespaceKey, file);
  }

  private static boolean isNamespaceAllowed(final String namespace, final List<String> list) {
    return StringUtil.isEmpty(namespace) ? list.isEmpty() : list.contains(namespace);
  }

  public final boolean isNamespaceAllowed(DomInvocationHandler handler, String namespace) {
    return myNamespaceKey == null || isNamespaceAllowed(namespace, getNamespaceList(handler));
  }

  @NotNull @NonNls
  public final String getNamespace(@NotNull XmlElement parentElement) {
    if (myNamespaceKey != null) {
      final List<String> strings = getAllowedNamespaces((XmlFile)parentElement.getContainingFile());
      if (!strings.isEmpty()) {
        return strings.get(0);
      }
    }

    if (parentElement instanceof XmlTag) {
      return ((XmlTag)parentElement).getNamespace();
    }
    if (parentElement instanceof XmlAttribute) {
      return ((XmlAttribute)parentElement).getNamespace();
    }
    if (parentElement instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)parentElement).getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return tag.getNamespace();
        }
      }
      return "";
    }
    throw new AssertionError("Can't get namespace of " + parentElement);
  }

  private List<String> getNamespaceList(final DomInvocationHandler handler) {
    return getAllowedNamespaces(handler.getFile());
  }

}
