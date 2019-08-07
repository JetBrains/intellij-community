// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

/**
 * Registers {@link WebModuleType} in IDEs which don't register neither {@link WebModuleType} nor {@link com.intellij.webcore.moduleType.PlatformWebModuleType}
 * but still need to use {@link WebModuleTypeBase} because they have Java plugin installed.
 */
final class WebModuleTypeRegistrar {
  WebModuleTypeRegistrar() {
    ModuleTypeManager moduleTypeManager = ModuleTypeManager.getInstance();
    if (moduleTypeManager.findByID(WebModuleTypeBase.WEB_MODULE) instanceof UnknownModuleType) {
      moduleTypeManager.registerModuleType(new WebModuleType());
    }
  }
}
