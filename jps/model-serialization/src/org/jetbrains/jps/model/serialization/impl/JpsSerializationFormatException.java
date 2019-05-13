// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.impl;

/**
 * Thrown when an xml configuration file has incorrect format (e.g. some required attributes are missing) so configuration of some {@link org.jetbrains.jps.model.JpsElement}
 * cannot be properly loaded.
 * <p/>
 * This exception doesn't prevent loading of other elements of {@link org.jetbrains.jps.model.JpsModel}.
 */
public class JpsSerializationFormatException extends RuntimeException {
  public JpsSerializationFormatException(String message) {
    super(message);
  }
}
