// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Computable;

import java.lang.reflect.Field;

class StubFieldAccessor implements Computable<ObjectStubSerializer> {
  private final Field myField;
  final String externalId;
  private volatile ObjectStubSerializer myFieldValue;

  StubFieldAccessor(String externalId, Field field) {
    this.externalId = externalId;
    myField = field;
  }

  @Override
  public ObjectStubSerializer compute() {
    ObjectStubSerializer delegate = myFieldValue;
    if (delegate == null) {
      try {
        myFieldValue = delegate = (ObjectStubSerializer)myField.get(null);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      if (!delegate.getExternalId().equals(externalId)) {
        throw new IllegalStateException(
          "External id mismatch in " + this + ". " +
          "Judging by extension declaration it should be " + externalId + " but " + delegate.getExternalId() + " is returned.");
      }
    }
    return delegate;
  }

  @Override
  public String toString() {
    return myField.toString();
  }
}
