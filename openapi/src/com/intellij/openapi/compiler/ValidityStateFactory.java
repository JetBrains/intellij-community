/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import java.io.DataInputStream;
import java.io.IOException;
/**
 * A factory for creating ValidityStateObjects
 * @see ValidityState
 */
public interface ValidityStateFactory {
  /**
   * Used for deserialization of ValidityState objects from compiler internal caches.
   * @see ValidityState
   */ 
  ValidityState createValidityState(DataInputStream is) throws IOException;
}
