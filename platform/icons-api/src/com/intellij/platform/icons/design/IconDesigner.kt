// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.cutoutMargin
import java.net.URI
import java.nio.file.Path

/** Individual methods act as layers and the order dictates in which order they are rendered. */
interface IconDesigner {
    fun image(resourceLoader: ImageResourceLocation, modifier: IconModifier = IconModifier)

    /** An image resource at [path] in [classLoader], for example `expui/general/add.svg`. */
    fun image(path: String, classLoader: ClassLoader, modifier: IconModifier = IconModifier)

    /**
     * An image file at [path], for example an icon file that the user chose. A relative [path] is kept relative, and is
     * resolved against the working directory of the process that loads the image. An invalid path draws nothing.
     */
    fun imageFile(path: Path, modifier: IconModifier = IconModifier)

    /**
     * An image at [url], a `file:` URL or a `jar:` URL. Images are not loaded from the network.
     *
     * @throws IllegalArgumentException when [url] is another kind of URL
     */
    fun imageUrl(url: URI, modifier: IconModifier = IconModifier)

    fun icon(iconDescriptor: IconDescriptor, modifier: IconModifier = IconModifier)

    fun box(modifier: IconModifier = IconModifier, builder: IconDesigner.() -> Unit)

    fun row(modifier: IconModifier = IconModifier, builder: IconDesigner.() -> Unit)

    fun column(modifier: IconModifier = IconModifier, builder: IconDesigner.() -> Unit)

    fun spacer(modifier: IconModifier = IconModifier)

    fun animation(modifier: IconModifier = IconModifier, builder: IconAnimationDesigner.() -> Unit)

    fun shape(color: Color, shape: Shape, modifier: IconModifier = IconModifier)
}

fun IconDesigner.badge(
    color: Color,
    shape: Shape = circle((2.8).dp),
    align: IconAlign = IconAlign.TopRight,
    cutout: IconUnit = 1.2.dp,
    modifier: IconModifier = IconModifier,
) {
    shape(color, shape, modifier.align(align).cutoutMargin(cutout))
}

interface IconAnimationDesigner {
    fun frame(duration: Long, builder: IconDesigner.() -> Unit)
    fun frame(fadeIn: Long, stay: Long, fadeOut: Long, builder: IconDesigner.() -> Unit)
}

fun IconAnimationDesigner.iconFrame(duration: Long, iconDescriptor: IconDescriptor) {
  frame(duration) { icon(iconDescriptor) }
}

fun IconAnimationDesigner.iconsFrames(duration: Long, iconDescriptors: List<IconDescriptor>) {
  iconDescriptors.forEach { iconFrame(duration, it) }
}

fun IconAnimationDesigner.iconsFrames(duration: Long, vararg iconDescriptors: IconDescriptor) {
  iconsFrames(duration, iconDescriptors.toList())
}
