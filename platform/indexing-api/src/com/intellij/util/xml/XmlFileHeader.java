// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import org.jetbrains.annotations.NonNls;

public class XmlFileHeader {
  public static final XmlFileHeader EMPTY = new XmlFileHeader(null, null, null, null);

  private final String myRootTagLocalName;
  private final String myRootTagNamespace;
  private final String myPublicId;
  private final String mySystemId;

  public XmlFileHeader(final String rootTagLocalName, final String rootTagNamespace, final String publicId, final String systemId) {
    myPublicId = publicId;
    myRootTagLocalName = rootTagLocalName;
    myRootTagNamespace = rootTagNamespace;
    mySystemId = systemId;
  }

  public String getPublicId() {
    return myPublicId;
  }

  public String getRootTagLocalName() {
    return myRootTagLocalName;
  }

  public String getRootTagNamespace() {
    return myRootTagNamespace;
  }

  public String getSystemId() {
    return mySystemId;
  }

  @Override
  public @NonNls String toString() {
    return "XmlFileHeader: name=" + myRootTagLocalName + "; namespace=" + myRootTagNamespace + "; publicId=" + myPublicId + "; systemId=" + mySystemId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof XmlFileHeader header)) return false;

    if (myPublicId != null ? !myPublicId.equals(header.myPublicId) : header.myPublicId != null) return false;
    if (myRootTagLocalName != null ? !myRootTagLocalName.equals(header.myRootTagLocalName) : header.myRootTagLocalName != null)
      return false;
    if (myRootTagNamespace != null ? !myRootTagNamespace.equals(header.myRootTagNamespace) : header.myRootTagNamespace != null)
      return false;
    if (mySystemId != null ? !mySystemId.equals(header.mySystemId) : header.mySystemId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (myRootTagLocalName != null ? myRootTagLocalName.hashCode() : 0);
    result = 31 * result + (myRootTagNamespace != null ? myRootTagNamespace.hashCode() : 0);
    result = 31 * result + (myPublicId != null ? myPublicId.hashCode() : 0);
    result = 31 * result + (mySystemId != null ? mySystemId.hashCode() : 0);
    return result;
  }
}
