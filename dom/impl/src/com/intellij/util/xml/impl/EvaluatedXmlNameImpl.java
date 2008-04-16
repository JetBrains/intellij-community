/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class EvaluatedXmlNameImpl implements EvaluatedXmlName {
  private static final Key<CachedValue<FactoryMap<String,List<String>>>> NAMESPACE_PROVIDER_KEY = Key.create("NamespaceProvider");
  private static final Map<EvaluatedXmlNameImpl,EvaluatedXmlNameImpl> ourInterned = new ConcurrentHashMap<EvaluatedXmlNameImpl,EvaluatedXmlNameImpl>();

  private final XmlName myXmlName;
  private final String myNamespaceKey;

  private EvaluatedXmlNameImpl(@NotNull final XmlName xmlName, @Nullable final String namespaceKey) {
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
    return createEvaluatedXmlName(name, namespaceKey);
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
    CachedValue<FactoryMap<String, List<String>>> value = file.getUserData(NAMESPACE_PROVIDER_KEY);
    if (value == null) {
      file.putUserData(NAMESPACE_PROVIDER_KEY, value = file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<FactoryMap<String, List<String>>>() {
          public Result<FactoryMap<String, List<String>>> compute() {
            final FactoryMap<String, List<String>> map = new ConcurrentFactoryMap<String, List<String>>() {
              protected List<String> create(final String key) {
                final DomFileDescription<?> description = DomManagerImpl.getDomManager(file.getProject()).getDomFileDescription(file);
                if (description == null) return Collections.emptyList();
                return description.getAllowedNamespaces(key, file);
              }
            };
            return Result.create(map, file);
          }
        }, false));
    }

    final List<String> list = value.getValue().get(myNamespaceKey);
    assert list != null;
    return list;
  }

  private static boolean isNamespaceAllowed(final String namespace, final List<String> list) {
    return list.contains(namespace) || StringUtil.isEmpty(namespace) && list.isEmpty();

  }

  public final boolean isNamespaceAllowed(String namespace, final XmlFile file) {
    return myNamespaceKey == null || isNamespaceAllowed(namespace, getNamespaceList(file));
  }

  @NotNull @NonNls
  public final String getNamespace(@NotNull XmlElement parentElement, final XmlFile file) {
    final String xmlElementNamespace = getXmlElementNamespace(parentElement);
    if (myNamespaceKey != null) {
      final List<String> strings = getAllowedNamespaces(file);
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

  private List<String> getNamespaceList(final XmlFile file) {
    return getAllowedNamespaces(file);
  }

  protected static EvaluatedXmlNameImpl createEvaluatedXmlName(@NotNull final XmlName xmlName, @Nullable final String namespaceKey) {
    final EvaluatedXmlNameImpl name = new EvaluatedXmlNameImpl(xmlName, namespaceKey);
    final EvaluatedXmlNameImpl interned = ourInterned.get(name);
    if (interned != null) {
      return interned;
    }
    ourInterned.put(name, name);
    return name;
  }
}
                