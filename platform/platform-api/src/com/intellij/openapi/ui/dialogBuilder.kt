/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import java.awt.Component
import javax.swing.JComponent

fun dialog(title: String,
           centerPanel: JComponent,
           resizable: Boolean = true,
           preferedFocusComponent: JComponent? = null,
           okActionEnabled: Boolean = true,
           project: Project? = null,
           parent: Component? = null): DialogBuilder {
  val builder = if (parent == null) DialogBuilder(project) else DialogBuilder(parent)
  builder
      .title(title)
      .centerPanel(centerPanel)
      .setPreferredFocusComponent(preferedFocusComponent)
  if (!resizable) {
    builder.resizable(false)
  }
  if (!okActionEnabled) {
    builder.okActionEnabled(false)
  }
  return builder
}