// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.NlsContexts

interface FileChooserInfo {
  val fileChooserTitle: @NlsContexts.DialogTitle String?
  val fileChooserDescription: @NlsContexts.Label String?
  val fileChooserDescriptor: FileChooserDescriptor
}