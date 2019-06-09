// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.SmartList

data class ReadConfiguration(val allowAnySubTypes: Boolean = false,
                             // loadClass for now doesn't support map or collection as host object
                             val loadClass: ((name: String, hostObject: Any) -> Class<*>?)? = null,
                             val beanConstructed: BeanConstructed? = null)

data class WriteConfiguration(val binary: Boolean = true,
                              val filter: SerializationFilter? = null,
                              val orderMapEntriesByKeys: Boolean = false,
                              val allowAnySubTypes: Boolean = false)

internal data class WriteContext(val writer: ValueWriter,
                                 val filter: SerializationFilter,
                                 val objectIdWriter: ObjectIdWriter?,
                                 val configuration: WriteConfiguration,
                                 val bindingProducer: BindingProducer)

internal interface ReadContext {
  val reader: ValueReader
  val objectIdReader: ObjectIdReader
  val bindingProducer: BindingProducer

  val configuration: ReadConfiguration

  /**
   * Each call will reset previously allocated result. For sub readers it is not a problem, because you must use [createSubContext] for this case.
   */
  fun allocateByteArrayOutputStream(): BufferExposingByteArrayOutputStream

  fun createSubContext(reader: ValueReader): ReadContext

  val errors: ReadErrors
}

data class ReadErrors(
  val unknownFields: MutableList<ReadError> = SmartList(),
  val fields: MutableList<ReadError> = SmartList()
) {
  fun report(logger: Logger) {
    if (unknownFields.isNotEmpty()) {
      logger.warn(unknownFields.joinToString("\n"))
    }
    if (fields.isNotEmpty()) {
      logger.warn(fields.joinToString("\n"))
    }
  }
}

data class ReadError(val message: String, val cause: Exception? = null)