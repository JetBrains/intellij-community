// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.swing

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor

/**
 * Layer for embedding swing icons. If the icon is just a swing wrapper containing our icon (and no modifier is passed),
 * it will be unpacked and embedded directly.
 */
fun IconDesigner.swingIcon(legacyIcon: javax.swing.Icon, modifier: IconModifier = IconModifier) {
    IconManager.getInstance().addSwingLayer(this, legacyIcon, modifier)
}

/**
 * Converts swing icon to be used by this API. If the icon is just a swing wrapper containing our icon (and no modifier
 * is passed), it will be unpacked and embedded directly.
 */
fun javax.swing.Icon.toNewIcon(modifier: IconModifier = IconModifier): Icon =
    IconManager.getInstance().toNewIcon(this, modifier)

/**
 * Converts specific Icon to swing Icon. Calling this method will perform synchronous image loading. If the Icon is just
 * a wrapped swing icon (and no modifier is set), it will be unpacked and returned directly.
 */
fun Icon.toSwingIcon(scale: IconScale = factor(1f)): javax.swing.Icon =
    IconManager.getInstance().toSwingIcon(this, scale)
