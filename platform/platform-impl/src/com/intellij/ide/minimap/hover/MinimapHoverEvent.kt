// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

internal sealed class MinimapHoverEvent {
  data class TargetChanged(val target: MinimapHoverTarget?) : MinimapHoverEvent()
}
