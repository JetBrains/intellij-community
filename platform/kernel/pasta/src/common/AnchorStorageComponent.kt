// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.AnchorId
import andel.editor.DocumentComponent
import andel.editor.RangeMarkerId
import andel.operation.Sticky
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal interface AnchorStorageComponent: DocumentComponent {
  fun addAnchor(anchorId: AnchorId, offset: Long, sticky: Sticky)
  fun addRangeMarker(markerId: RangeMarkerId, from: Long, to: Long, closedLeft: Boolean, closedRight: Boolean)
  fun removeAnchor(anchorId: AnchorId)
  fun removeRangeMarker(rangeMarkerId: RangeMarkerId)
}
