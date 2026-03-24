// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ide.ui.colors

import com.intellij.ui.SimpleTextAttributes
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun SerializableSimpleTextAttributes.attributes(): SimpleTextAttributes {
  return SimpleTextAttributes(bgColor?.color(),
                              fgColor?.color(),
                              waveColor?.color(),
                              style)
}

@ApiStatus.Experimental
fun SimpleTextAttributes.rpcId(): SerializableSimpleTextAttributes {
  return SerializableSimpleTextAttributes(bgColor?.rpcId(),
                                          fgColor?.rpcId(),
                                          waveColor?.rpcId(),
                                          style)
}

@Serializable
@ApiStatus.Experimental
class SerializableSimpleTextAttributes internal constructor(
  @JvmField internal val bgColor: ColorId?,
  @JvmField internal val fgColor: ColorId?,
  @JvmField internal val waveColor: ColorId?,
  @JvmField internal val style: Int,
)