package com.intellij.database.data.types

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object UuidConverters {
  @JvmStatic
  @OptIn(ExperimentalUuidApi::class)
  fun toJavaUuid(uuid: Uuid): java.util.UUID = uuid.toJavaUuid()
}