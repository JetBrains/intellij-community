// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;

public interface XmlRpcServer {
  void addHandler(String name, Object handler);

  boolean hasHandler(String name);

  void removeHandler(String name);

  static XmlRpcServer getInstance() {
    return ApplicationManager.getApplication().getService(XmlRpcServer.class);
  }
}
