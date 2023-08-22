// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

internal sealed class UIState {

  object Reset : UIState()

  class ScrollToAnchor(val anchor: String) : UIState()

  class RestoreFromSnapshot(val snapshot: UISnapshot) : UIState()
}
