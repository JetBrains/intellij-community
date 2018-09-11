// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.memcached;

import com.intellij.platform.onair.storage.api.Address;
import net.spy.memcached.ops.OperationCallback;

public interface AddressOperationCallback extends OperationCallback {

  void gotData(Address key, int flags, byte[] data);
}
