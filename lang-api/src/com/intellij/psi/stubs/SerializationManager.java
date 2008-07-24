/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class SerializationManager {
  public static SerializationManager getInstance() {
    return ApplicationManager.getApplication().getComponent(SerializationManager.class);
  }

  public abstract void registerSerializer(StubSerializer<? extends StubElement> serializer);

  public abstract void serialize(StubElement rootStub, OutputStream stream);

  public abstract StubElement deserialize(InputStream stream);

  public abstract StubSerializer getSerializer(StubElement rootStub);

  public abstract boolean isNameStorageCorrupted();

  public abstract void repairNameStorage();
}