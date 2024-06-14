// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import java.awt.Point

internal fun Point.translateNew(dx: Int, dy: Int) : Point = Point(x + dx, y + dy)