/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public abstract class SerializationManager {
  public static SerializationManager getInstance() {
    return ApplicationManager.getApplication().getComponent(SerializationManager.class);
  }

  public abstract <T extends StubElement> void registerSerializer(StubSerializer<T> serializer);

  public abstract void serialize(StubElement rootStub, DataOutputStream stream);

  public abstract StubElement deserialize(DataInputStream stream);

  public abstract StubSerializer getSerializer(StubElement rootStub);
}