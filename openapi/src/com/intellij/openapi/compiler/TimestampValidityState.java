/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A simple implementation of ValidityState that is enough for most cases.
 * The file is considered modified if its timeastamp is changed.
 */
public final class TimestampValidityState implements ValidityState {
  private final long myTimestamp;

  public TimestampValidityState(long timestamp) {
    myTimestamp = timestamp;
  }

  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof TimestampValidityState)) {
      return false;
    }
    return myTimestamp == ((TimestampValidityState)otherState).myTimestamp;
  }

  public void save(DataOutputStream os) throws IOException {
    os.writeLong(myTimestamp);
  }
}
