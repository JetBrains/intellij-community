// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.sun.jdi.ObjectReference
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

/** Identifies an object independently of the type data stored in its marker. */
@ApiStatus.Internal
open class JavaObjectReferenceKey(val reference: ObjectReference) {
  final override fun equals(other: Any?): Boolean = other is JavaObjectReferenceKey && reference == other.reference

  final override fun hashCode(): Int = reference.hashCode()
}

@ApiStatus.Internal
class JavaMarkedObject(
  reference: ObjectReference,
  val signature: String,
  val typeName: String,
) : JavaObjectReferenceKey(reference) {
  companion object {
    @JvmStatic
    fun create(value: JavaValue): Promise<JavaObjectReferenceKey> {
      val suspendContext = value.evaluationContext.suspendContext
      return suspendContext.coroutineScope.future<JavaObjectReferenceKey> {
        withDebugContext(suspendContext) {
          val reference = value.descriptor.getValue() as ObjectReference
          val type = reference.type()
          JavaMarkedObject(reference, type.signature(), type.name())
        }
      }.asPromise()
    }
  }
}
