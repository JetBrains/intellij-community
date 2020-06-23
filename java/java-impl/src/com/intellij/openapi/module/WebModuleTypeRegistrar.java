// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.Disposable;

/**
 * Registers {@link WebModuleType} in IDEs which don't register neither {@link WebModuleType} nor {@link com.intellij.webcore.moduleType.PlatformWebModuleType}
 * but still need to use {@link WebModuleTypeBase} because they have Java plugin installed.
 */
final class WebModuleTypeRegistrar implements Disposable {
  private final WebModuleType myWebModuleType;

  WebModuleTypeRegistrar() {
    ModuleTypeManager moduleTypeManager = ModuleTypeManager.getInstance();
    if (moduleTypeManager.findByID(WebModuleTypeBase.WEB_MODULE) instanceof UnknownModuleType) {
      myWebModuleType = new WebModuleType();
      moduleTypeManager.registerModuleType(myWebModuleType);
    }
    else {
      myWebModuleType = null;
    }
  }

  @Override
  public void dispose() {
    if (myWebModuleType != null) {
      ModuleTypeManager.getInstance().unregisterModuleType(myWebModuleType);
    }
  }
}
