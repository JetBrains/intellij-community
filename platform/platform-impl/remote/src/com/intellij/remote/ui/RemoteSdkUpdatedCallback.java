// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ui;

import com.intellij.remote.RemoteSdkProperties;

public interface RemoteSdkUpdatedCallback {
  void updated(RemoteSdkProperties sdkProperties);
}
