// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import java.lang.reflect.Constructor

// not fully initialized object may be passed (only created instance without properties) if an object has PropertyMapping annotation
typealias BeanConstructed = (instance: Any) -> Any

class NonDefaultConstructorInfo(@JvmField val names: List<String>, @JvmField val constructor: Constructor<*>)

typealias PropertyMappingProvider = (beanClass: Class<*>) -> NonDefaultConstructorInfo?

data class ReadConfiguration(@JvmField val allowAnySubTypes: Boolean = false,
                             // loadClass for now doesn't support a map or collection as a host object
                             @JvmField val loadClass: ((name: String, hostObject: Any) -> Class<*>?)? = null,
                             @JvmField val beanConstructed: BeanConstructed? = null,
                             @JvmField val resolvePropertyMapping: PropertyMappingProvider? = null)

data class WriteConfiguration(@JvmField val binary: Boolean = true,
                              @JvmField val filter: SerializationFilter? = null,
                              @JvmField val orderMapEntriesByKeys: Boolean = false,
                              @JvmField val allowAnySubTypes: Boolean = false)

internal data class WriteContext(@JvmField val writer: ValueWriter,
                                 @JvmField val filter: SerializationFilter,
                                 @JvmField val objectIdWriter: ObjectIdWriter?,
                                 @JvmField val configuration: WriteConfiguration,
                                 @JvmField val bindingProducer: BindingProducer)

internal interface ReadContext {
  val reader: ValueReader
  val objectIdReader: ObjectIdReader
  val bindingProducer: BindingProducer

  val configuration: ReadConfiguration

  /**
   * Each call will reset a previously allocated result.
   * For sub readers it is not a problem, because you must use [createSubContext] for this case.
   */
  fun allocateByteArrayOutputStream(): BufferExposingByteArrayOutputStream

  fun createSubContext(reader: ValueReader): ReadContext

  fun checkCancelled()

  val errors: ReadErrors
}

internal data class ReadErrors(
  @JvmField val unknownFields: MutableList<ReadError> = ArrayList(),
  @JvmField val fields: MutableList<ReadError> = ArrayList()
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

internal data class ReadError(@JvmField val message: String, @JvmField val cause: Exception? = null)