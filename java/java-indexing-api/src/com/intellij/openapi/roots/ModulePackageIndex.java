// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;

public abstract class ModulePackageIndex extends PackageIndex {
  public static ModulePackageIndex getInstance(Module module) {
    return module.getService(ModulePackageIndex.class);
  }
}
