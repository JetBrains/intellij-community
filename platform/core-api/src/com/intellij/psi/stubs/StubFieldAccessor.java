// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import java.lang.reflect.Field;
import java.util.function.Supplier;

final class StubFieldAccessor implements Supplier<ObjectStubSerializer<?, Stub>> {
  private final Field myField;
  final String externalId;
  private volatile ObjectStubSerializer<?, Stub> myFieldValue;

  StubFieldAccessor(String externalId, Field field) {
    this.externalId = externalId;
    myField = field;
    try {
      field.setAccessible(true);
    }
    catch (SecurityException ignore) {
    }
  }

  @Override
  public ObjectStubSerializer<?, Stub> get() {
    ObjectStubSerializer<?, Stub> delegate = myFieldValue;
    if (delegate == null) {
      try {
        //noinspection unchecked
        myFieldValue = delegate = (ObjectStubSerializer<?, Stub>)myField.get(null);
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
