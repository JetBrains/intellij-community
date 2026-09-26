// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

/**
 * @author Konstantin Bulenkov
 */
class TransparentSplitter : OnePixelSplitter {
  constructor()
  constructor(vertical: Boolean) : super(vertical)
  constructor(vertical: Boolean, proportionKey: String, defaultProportion: Float) : super(vertical, proportionKey, defaultProportion)
  constructor(vertical: Boolean, proportion: Float) : super(vertical, proportion)
  constructor(proportionKey: String, defaultProportion: Float) : super(proportionKey, defaultProportion)
  constructor(proportion: Float) : super(proportion)
  constructor(vertical: Boolean, proportion: Float, minProp: Float, maxProp: Float) : super(vertical, proportion, minProp, maxProp)

  override fun getDividerWidth(): Int = 0
}