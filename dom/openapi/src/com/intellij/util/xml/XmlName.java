/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class XmlName implements Comparable<XmlName> {
  private final String myLocalName;
  private final String myNamespaceKey;

  public XmlName(@NotNull @NonNls final String localName) {
    this(localName, null);
  }

  public XmlName(@NotNull @NonNls final String localName, @Nullable final String namespaceKey) {
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


  public int compareTo(XmlName o) {
    final int i = myLocalName.compareTo(o.myLocalName);
    if (i != 0) return i;
    if (Comparing.equal(myNamespaceKey, o.myNamespaceKey)) return 0;
    if (myNamespaceKey == null) return -1;
    if (o.myNamespaceKey == null) return 1;
    return myNamespaceKey.compareTo(o.myNamespaceKey);
  }

  public String toString() {
    return myNamespaceKey + ":" + myLocalName;
  }
}
