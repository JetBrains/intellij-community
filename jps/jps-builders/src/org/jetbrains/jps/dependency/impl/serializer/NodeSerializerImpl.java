// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl.serializer;

import org.jetbrains.jps.dependency.NodeSerializer;
import org.jetbrains.jps.dependency.SerializableGraphElement;

public abstract class NodeSerializerImpl<T> implements NodeSerializer {
  private final int serializerId;
  protected final Class<T> supportedClassType;

  public NodeSerializerImpl(Class<T> type) {
    this.serializerId = SerializerIdGenerator.getNextId();
    this.supportedClassType = type;
  }

  @Override
  public int getId() {
    return serializerId;
  }

  @Override
  public <T extends SerializableGraphElement> boolean isSupported(Class<T> elemClass) {
    return elemClass == supportedClassType;
  }
}
