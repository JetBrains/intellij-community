// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.ui

import com.intellij.icons.AllIcons
import javax.swing.Icon

object DefaultExternalSystemIconProvider : ExternalSystemIconProvider {

  override val reloadIcon: Icon = AllIcons.Actions.BuildLoadChanges
}