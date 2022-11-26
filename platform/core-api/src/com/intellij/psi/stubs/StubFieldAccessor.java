// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import java.lang.reflect.Field;
import java.util.function.Supplier;

final class StubFieldAccessor implements Supplier<ObjectStubSerializer<?, ? extends Stub>> {
  final String externalId;
  private final Field myField;
  private volatile ObjectStubSerializer<?, Stub> myFieldValue;

  StubFieldAccessor(String externalId, Field field) {
    this.externalId = externalId;
    this.myField = field;
    try {
      field.setAccessible(true);
    }
    catch (SecurityException ignore) { }
  }

  @Override
  public ObjectStubSerializer<?, Stub> get() {
    ObjectStubSerializer<?, Stub> delegate = myFieldValue;
    if (delegate == null) {
      try {
        @SuppressWarnings("unchecked") ObjectStubSerializer<?, Stub> value = (ObjectStubSerializer<?, Stub>)myField.get(null);
        myFieldValue = delegate = value;
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (ClassCastException e) {
        throw new IllegalArgumentException(myField + " is not assignable to 'ObjectStubSerializer'", e);
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
