// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.annotations.Attribute;

public final class ServiceDescriptor {
  @Attribute("serviceInterface")
  public String serviceInterface;

  @Attribute("serviceImplementation")
  public String serviceImplementation;

  @Attribute("testServiceImplementation")
  public String testServiceImplementation;

  @Attribute("overrides")
  public boolean overrides;

  public String getInterface() {
    return serviceInterface != null ? serviceInterface : getImplementation();
  }

  public String getImplementation() {
    return testServiceImplementation != null && ApplicationManager.getApplication().isUnitTestMode() ? testServiceImplementation : serviceImplementation;
  }

  @Override
  public String toString() {
    return "ServiceDescriptor(interface=" + getInterface() + ", implementation=" + getImplementation() + ")";
  }
}
