/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.storage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A simple implementation of ValidityState that is enough for most cases.
 * The file is considered modified if its timestamp is changed.
 */
public final class TimestampValidityState implements ValidityState {
  private final long myTimestamp;

  /**
   * Loads the validity state from the specified stream.
   *
   * @param is the stream to load the validity state from.
   * @throws IOException if the stream read fails.
   */
  public TimestampValidityState(DataInput is) throws IOException{
    myTimestamp = is.readLong();
  }

  /**
   * Creates a validity state with the specified timestamp.
   *
   * @param timestamp the timestamp for the validity state.
   */
  public TimestampValidityState(long timestamp) {
    myTimestamp = timestamp;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public boolean equalsTo(ValidityState otherState) {
    return otherState instanceof TimestampValidityState && myTimestamp == ((TimestampValidityState)otherState).myTimestamp;
  }

  /**
   * Saves the validity state to the specified stream.
   *
   * @param out
   * @throws java.io.IOException if the stream write fails.
   */
  public void save(DataOutput out) throws IOException {
    out.writeLong(myTimestamp);
  }
}
