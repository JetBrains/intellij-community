/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;

/**
 * @author peter
*/
public class XmlName implements Comparable<XmlName> {
  private final String myLocalName;
  private final String myNamespaceKey;

  public XmlName(@NotNull final String localName) {
    this(localName, null);
  }

  public XmlName(@NotNull final String localName, @Nullable final String namespaceKey) {
    myLocalName = localName;
    myNamespaceKey = namespaceKey;
  }

  @NotNull
  public final String getLocalName() {
    return myLocalName;
  }

  @Nullable
  public final String getNamespaceKey() {
    return myNamespaceKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final XmlName xmlName = (XmlName)o;

    if (!myLocalName.equals(xmlName.myLocalName)) return false;
    if (Comparing.equal(myNamespaceKey, xmlName.myNamespaceKey)) return true;

    if (myNamespaceKey != null ? !myNamespaceKey.equals(xmlName.myNamespaceKey) : xmlName.myNamespaceKey != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myLocalName.hashCode();
    result = 31 * result + (myNamespaceKey != null ? myNamespaceKey.hashCode() : 0);
    return result;
  }

  @Nullable
  public static XmlName create(@NotNull String name, Type type, @Nullable JavaMethod javaMethod) {
    final Class<?> aClass = getErasure(type);
    if (aClass == null) return null;
    String key = getNamespaceKey(aClass);
    if (key == null && javaMethod != null) {
      for (final Method method : javaMethod.getSignature().getAllMethods(javaMethod.getDeclaringClass())) {
        final String key1 = getNamespaceKey(method.getDeclaringClass());
        if (key1 != null) {
          key = key1;
        }
      }
    }
    return new XmlName(name, key);
  }

  @Nullable
  private static Class<?> getErasure(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getErasure(((ParameterizedType)type).getRawType());
    }
    if (type instanceof TypeVariable) {
      for (final Type bound : ((TypeVariable)type).getBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    if (type instanceof WildcardType) {
      final WildcardType wildcardType = (WildcardType)type;
      for (final Type bound : wildcardType.getUpperBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    return null;
  }


  @Nullable
  private static String getNamespaceKey(@NotNull Class<?> type) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(type, Namespace.class);
    return namespace != null ? namespace.value() : null;
  }

  @Nullable
  public static XmlName create(@NotNull final String name, final JavaMethod method) {
    return create(name, method.getGenericReturnType(), method);
  }

  public int compareTo(XmlName o) {
    final int i = myLocalName.compareTo(o.myLocalName);
    if (i != 0) return i;
    if (Comparing.equal(myNamespaceKey, o.myNamespaceKey)) return 0;
    if (myNamespaceKey == null) return -1;
    if (o.myNamespaceKey == null) return 1;
    return myNamespaceKey.compareTo(o.myNamespaceKey);
  }
}
