/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.model;

import java.io.IOException;
import java.io.Serializable;

/**
 * Instance of this interface is used inside {@link DataNode} to (de)serialize the actual data stored inside a graph node.
 * Instances should be serializable themselves, as they will be stored along with the data.
 * @param <T> data type to be serialized.
 */
public interface DataNodeSerializer<T> extends Serializable {
  byte[] getBytes(T data) throws IOException;

  T readData(byte[] data, ClassLoader... classLoaders) throws IOException, ClassNotFoundException;
}
