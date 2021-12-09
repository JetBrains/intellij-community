// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.ui

internal sealed class ScrollingPosition {

  object Keep : ScrollingPosition()

  object Reset : ScrollingPosition()

  class Anchor(val anchor: String) : ScrollingPosition()
}
