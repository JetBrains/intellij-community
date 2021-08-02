// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

public class PlatformCoreDataKeys extends CommonDataKeys {
  public static final DataKey<VirtualFile> PROJECT_FILE_DIRECTORY = DataKey.create("context.ProjectFileDirectory");

  public static final DataKey<Module> MODULE = DataKey.create("module");
}
