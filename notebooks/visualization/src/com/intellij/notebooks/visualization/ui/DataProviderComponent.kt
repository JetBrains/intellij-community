// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import javax.swing.JComponent

interface DataProviderComponent {
  fun retrieveDataProvider(): JComponent
}
