package org.jetbrains.jps.incremental.storage;

import java.io.DataOutput;
import java.io.IOException;

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
   * @throws java.io.IOException if the save operation failed because of an I/O error.
   */
  void save(DataOutput out) throws IOException;
}
