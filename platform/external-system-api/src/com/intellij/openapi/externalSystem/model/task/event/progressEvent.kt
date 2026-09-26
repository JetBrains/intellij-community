// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.io.Serializable

open class ExternalSystemProgressEvent<T : OperationDescriptor>(
  val eventId: String,
  val parentEventId: String?,
  val descriptor: T,
) : Serializable {

  val displayName: String
    get() = descriptor.displayName

  val eventTime: Long
    get() = descriptor.eventTime
}

class ExternalSystemStartEvent<T : OperationDescriptor>(
  eventId: String,
  parentEventId: String?,
  descriptor: T,
) : ExternalSystemProgressEvent<T>(eventId = eventId, parentEventId = parentEventId, descriptor = descriptor)

class ExternalSystemFinishEvent<T : OperationDescriptor>(
  eventId: String,
  parentEventId: String?,
  descriptor: T,
  val operationResult: OperationResult,
) : ExternalSystemProgressEvent<T>(eventId = eventId, parentEventId = parentEventId, descriptor = descriptor)

/**
 * An event with textual description.
 *
 * @property description Textual description of the event. Arbitrary additional information about status update.
 */
class ExternalSystemMessageEvent<T : OperationDescriptor>(
  eventId: String,
  parentEventId: String?,
  descriptor: T,
  val isStdOut: Boolean,
  val message: @Nls String?,
  val description: String?,
) : ExternalSystemProgressEvent<T>(eventId = eventId, parentEventId = parentEventId, descriptor = descriptor) {

  constructor(
    eventId: String,
    parentEventId: String?,
    descriptor: T,
    description: @NlsSafe String?,
  ) : this(eventId = eventId,
           parentEventId = parentEventId,
           descriptor = descriptor,
           isStdOut = true,
           message = description,
           description = description)
}

/**
 * An event that informs about an interim result of the operation.
 *
 * @property total The total amount of work that the build operation is in the progress of performing, or -1 if not known.
 * @property progress The amount of work already performed by the build operation.
 * @property unit The measure used to express the amount of work.
 */
class ExternalSystemStatusEvent<T : OperationDescriptor> @JvmOverloads constructor(
  eventId: String,
  parentEventId: String?,
  descriptor: T,
  val total: Long,
  val progress: Long,
  val unit: String,
  val description: String? = null,
) : ExternalSystemProgressEvent<T>(eventId = eventId, parentEventId = parentEventId, descriptor = descriptor)
