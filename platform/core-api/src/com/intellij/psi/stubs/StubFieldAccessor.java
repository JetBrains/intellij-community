// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Field;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class StubFieldAccessor implements Supplier<ObjectStubSerializer<?, ? extends Stub>> {
  public final String externalId;
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
        Object object = myField.get(null);
        ObjectStubSerializer<?, Stub> serializer;
        if (object instanceof ObjectStubSerializer) {
          serializer = (ObjectStubSerializer<?, Stub>)object;
        }
        else if (object instanceof IElementType) {
          IElementType elementType = ((IElementType)object);
          serializer = StubElementRegistryService.getInstance().getStubSerializer(elementType);
        }
        else {
          throw new IllegalStateException(object + " is not an instance of ObjectStubSerializer nor IElementType with registered stub serializer");
        }
        myFieldValue = delegate = serializer;
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
