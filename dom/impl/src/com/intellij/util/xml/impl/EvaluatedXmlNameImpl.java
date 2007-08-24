/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class EvaluatedXmlNameImpl implements EvaluatedXmlName {
  private static final Key<FactoryMap<String,CachedValue<List<String>>>> NAMESPACE_PROVIDER_KEY = Key.create("NamespaceProvider");
  private static final UserDataCache<FactoryMap<String,CachedValue<List<String>>>, XmlFile, Object> ourNamespaceCache =
    new UserDataCache<FactoryMap<String,CachedValue<List<String>>>, XmlFile, Object>() {
      protected FactoryMap<String,CachedValue<List<String>>> compute(final XmlFile file, final Object o) {
        return new FactoryMap<String, CachedValue<List<String>>>() {
          protected CachedValue<List<String>> create(final String key) {
            return file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<List<String>>() {
              public Result<List<String>> compute() {
                final DomFileDescription<?> description = DomManagerImpl.getDomManager(file.getProject()).getDomFileDescription(file);
                if (description == null) {
                  return new CachedValueProvider.Result<List<String>>(Collections.<String>emptyList(), file);
                }
                return new CachedValueProvider.Result<List<String>>(description.getAllowedNamespaces(key, file), file);
              }
            }, false);
          }
        };
      }
    };


  private final XmlName myXmlName;
  private final String myNamespaceKey;

  protected EvaluatedXmlNameImpl(@NotNull final XmlName xmlName, @Nullable final String namespaceKey) {
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

  public final EvaluatedXmlName evaluateChildName(@NotNull final XmlName name) {
    String namespaceKey = name.getNamespaceKey();
    if (namespaceKey == null) {
      namespaceKey = myNamespaceKey;
    }
    return new EvaluatedXmlNameImpl(name, namespaceKey);
  }

  public String toString() {
    return (myNamespaceKey == null ? "" : myNamespaceKey + " : ") + myXmlName.getLocalName();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EvaluatedXmlNameImpl xmlName = (EvaluatedXmlNameImpl)o;

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

  @NotNull
  private List<String> getAllowedNamespaces(final XmlFile file) {
    final List<String> list = ourNamespaceCache.get(NAMESPACE_PROVIDER_KEY, file, null).get(myNamespaceKey).getValue();
    assert list != null;
    return list;
  }

  private static boolean isNamespaceAllowed(final String namespace, final List<String> list) {
    return StringUtil.isEmpty(namespace) ? list.isEmpty() : list.contains(namespace);
  }

  public final boolean isNamespaceAllowed(DomInvocationHandler handler, String namespace) {
    return myNamespaceKey == null || isNamespaceAllowed(namespace, getNamespaceList(handler));
  }

  @NotNull @NonNls
  public final String getNamespace(@NotNull XmlElement parentElement) {
    final String xmlElementNamespace = getXmlElementNamespace(parentElement);
    if (myNamespaceKey != null) {
      final List<String> strings = getAllowedNamespaces((XmlFile)parentElement.getContainingFile());
      if (!strings.isEmpty() && !strings.contains(xmlElementNamespace)) {
        return strings.get(0);
      }
    }
    return xmlElementNamespace;
  }

  private static String getXmlElementNamespace(final XmlElement parentElement) {
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
