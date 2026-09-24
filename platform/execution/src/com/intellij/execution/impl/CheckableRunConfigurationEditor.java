// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

public interface CheckableRunConfigurationEditor<Settings> {
  //override this method to provide light check
  // for your run configuration data in editor for warnings
  void checkEditorData(Settings s);
}
