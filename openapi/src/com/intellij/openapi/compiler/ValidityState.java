/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Instances of this class are associated with the files processed by compilers.
 * A file is considered modified if currently associated ValidityState differs from the previously stored ValiditySate for this file
 */
public interface ValidityState {
  /**
   * Compares this validity state to other ValidityState
   * @param otherState
   * @return true if states can be considered equal, false otherwise
   */
  boolean equalsTo(ValidityState otherState);
  /**
   * Invoked by make subsystem in order to store the sate
   * @param os
   * @throws IOException
   */
  void save(DataOutputStream os) throws IOException;
}
