// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

/**
 * @author Sergey.Malenkov
 */
public class EditorOptionsTopHitProvider extends EditorOptionsTopHitProviderBase.NoPrefix {
  @Override
  protected Configurable getConfigurable(Project project) {
    return new EditorOptionsPanel();
  }
}
