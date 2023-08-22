// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.properties

import com.intellij.openapi.util.NlsContexts

interface PropertiesInfo {
  val dialogTitle: @NlsContexts.DialogTitle String
  val dialogTooltip: @NlsContexts.Tooltip String
  val dialogLabel: @NlsContexts.Label String
  val dialogEmptyState: @NlsContexts.StatusText String
  val dialogOkButton: @NlsContexts.Button String
}