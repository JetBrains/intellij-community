// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream

data class ReadConfiguration(val allowAnySubTypes: Boolean = false,
                             val classLoader: ClassLoader? = null,
                             val beanConstructed: BeanConstructed? = null)

data class WriteConfiguration(val binary: Boolean = true,
                              val filter: SerializationFilter? = null,
                              val orderMapEntriesByKeys: Boolean = false,
                              val allowAnySubTypes: Boolean = false)

data class WriteContext(val writer: ValueWriter,
                        val filter: SerializationFilter,
                        val objectIdWriter: ObjectIdWriter?,
                        val configuration: WriteConfiguration,
                        val bindingProducer: BindingProducer<RootBinding>)

interface ReadContext {
  val reader: ValueReader
  val objectIdReader: ObjectIdReader
  val bindingProducer: BindingProducer<RootBinding>

  val configuration: ReadConfiguration

  /**
   * Each call will reset previously allocated result. For sub readers it is not a problem, because you must use [createSubContext] for this case.
   */
  fun allocateByteArrayOutputStream(): BufferExposingByteArrayOutputStream

  fun createSubContext(reader: ValueReader): ReadContext
}