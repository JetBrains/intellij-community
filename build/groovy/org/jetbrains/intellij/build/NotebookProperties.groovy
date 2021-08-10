// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

@CompileStatic
class NotebookProperties {
  /** Common, language-agnostic modules for notebook support (like for Jupyter, or R Markdown). */
  static final List<String> NOTEBOOK_IMPLEMENTATION_MODULES = [
    "intellij.notebooks.visualization",
  ]
}
