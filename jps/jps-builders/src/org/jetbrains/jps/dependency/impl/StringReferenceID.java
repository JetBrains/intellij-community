// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.jps.dependency.ReferenceID;

public final class StringReferenceID implements ReferenceID {
  private final String myValue;

  public StringReferenceID(String value) {
    myValue = value;
  }

  public String getValue() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final StringReferenceID that = (StringReferenceID)o;

    if (!myValue.equals(that.myValue)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }

  @Override
  public String toString() {
    return "REF_ID:" + myValue;
  }
}
