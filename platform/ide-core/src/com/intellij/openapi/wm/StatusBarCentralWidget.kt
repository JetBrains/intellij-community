// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import javax.swing.JComponent

interface StatusBarCentralWidget : StatusBarWidget {
  fun getCentralStatusBarComponent() : JComponent
}