// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;


public interface ModuleConfigurationEditorProviderEx extends ModuleConfigurationEditorProvider {
  /**
   * If this method returns true, the editors returned from this provider comprise the entirety of the editors to be shown for this module,
   * and no editors from other providers should be displayed.
   *
   * @return true to suppress editors from other providers, false otherwise.
   */
  boolean isCompleteEditorSet();
}
