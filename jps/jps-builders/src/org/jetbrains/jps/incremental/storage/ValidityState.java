// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import java.io.DataOutput;

/**
 * Instances of this class are associated with the files processed by compilers.
 * A file is considered modified if currently associated ValidityState differs from the previously stored ValiditySate for this file.
 */
public interface ValidityState {
  /**
   * Compares this validity state to other ValidityState.
   *
   * @param otherState the state to compare with.
   * @return true if states can be considered equal, false otherwise.
   */
  boolean equalsTo(ValidityState otherState);
  /**
   * Invoked by make subsystem in order to store the state.
   *
   * @param out the output to which the state should be stored.
   */
  void save(DataOutput out);
}
