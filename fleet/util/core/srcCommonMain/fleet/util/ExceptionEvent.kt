// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

/**
 * @param readableComponentName if not null, it will be shown to the user to identify the component where it happened
 */
@kotlinx.serialization.Serializable
data class ExceptionEvent(
  val exception: ThrowableHolder,
  val logMessage: String?,
  /**
   * componentName is a readable backend/fsd/etc id for identifying problem source by the user
   */
  val readableComponentName: String? = null
)
