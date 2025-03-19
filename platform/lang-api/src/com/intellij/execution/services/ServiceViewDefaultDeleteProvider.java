// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.application.ApplicationManager;
import jdk.jfr.Experimental;

@Experimental
public interface ServiceViewDefaultDeleteProvider extends DeleteProvider {

  static ServiceViewDefaultDeleteProvider getInstance() {
    return ApplicationManager.getApplication().getService(ServiceViewDefaultDeleteProvider.class);
  }

}
