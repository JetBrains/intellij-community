// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.grpc

import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import io.grpc.Status
import io.grpc.Status.Code.*
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import org.jetbrains.annotations.VisibleForTesting
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.reflect.KClass

@Suppress("DataClassPrivateConstructor")
data class ExceptionAsStatus private constructor(val status: Status,
                                                 val exceptionDescriptor: ExceptionDescriptor<*>) {

  data class ExceptionDescriptor<T : Throwable> internal constructor(
    val type: KClass<out T>,
    val constructor: (String?, Throwable?) -> T
  ) {
    companion object {
      internal inline fun <reified T : Throwable> withThrowable(
        noinline constructor: (String?, Throwable?) -> T
      ) = ExceptionDescriptor(T::class, constructor)

      internal inline fun <reified T : Throwable> withInitCause(
        noinline constructor: (String?) -> T
      ) = ExceptionDescriptor(T::class) { s, cause -> constructor(s).initCause(cause) }
    }

  }

  companion object {
    private inline operator fun <reified T : Throwable> invoke(
      status: Status,
      noinline exceptionConstructor: (String?, Throwable?) -> T
    ) = ExceptionAsStatus(status, ExceptionDescriptor.withThrowable(exceptionConstructor))

    // @formatter:off
    @VisibleForTesting
    internal val KNOWN_EXCEPTIONS: LinkedHashMap<Class<out Throwable>, ExceptionAsStatus> = listOf(
      // the order matters! should be from the most generic down to more specific
      ExceptionDescriptor(Throwable::class, ::RuntimeException)            asStatus DATA_LOSS,

      ExceptionDescriptor.withThrowable(::Exception)                       asStatus DATA_LOSS,
      ExceptionDescriptor.withThrowable(::RuntimeException)                asStatus DATA_LOSS,
      ExceptionDescriptor.withThrowable(::Error)                           asStatus DATA_LOSS,

      ExceptionDescriptor.withThrowable(::AssertionError)                  asStatus DATA_LOSS,
      ExceptionDescriptor.withInitCause(::NoClassDefFoundError)            asStatus DATA_LOSS,
      ExceptionDescriptor.withInitCause(::ClassCastException)              asStatus DATA_LOSS,
      ExceptionDescriptor.withInitCause(::NullPointerException)            asStatus DATA_LOSS,
      ExceptionDescriptor.withInitCause(::KotlinNullPointerException)      asStatus DATA_LOSS,
      ExceptionDescriptor.withThrowable(::ConcurrentModificationException) asStatus DATA_LOSS,

      ExceptionDescriptor.withThrowable(::UnsupportedOperationException)   asStatus UNIMPLEMENTED,

      ExceptionDescriptor.withInitCause(::NoSuchElementException)          asStatus OUT_OF_RANGE,

      ExceptionDescriptor.withInitCause(::IndexOutOfBoundsException)       asStatus OUT_OF_RANGE,
      ExceptionDescriptor.withInitCause(::StringIndexOutOfBoundsException) asStatus OUT_OF_RANGE,
      ExceptionDescriptor.withInitCause(::ArrayIndexOutOfBoundsException)  asStatus OUT_OF_RANGE,

      ExceptionDescriptor.withThrowable(::IllegalStateException)           asStatus FAILED_PRECONDITION,
      ExceptionDescriptor.withInitCause(::CancellationException)           asStatus CANCELLED,
      ExceptionDescriptor.withInitCause(::QuotaExceededException)          asStatus RESOURCE_EXHAUSTED,

      ExceptionDescriptor.withThrowable(::IllegalArgumentException)        asStatus INVALID_ARGUMENT,
      ExceptionDescriptor.withInitCause(::IllegalThreadStateException)     asStatus INVALID_ARGUMENT,
      ExceptionDescriptor.withInitCause(::NumberFormatException)           asStatus INVALID_ARGUMENT,

      ExceptionDescriptor.withThrowable(::IOException)                     asStatus NOT_FOUND,
      ExceptionDescriptor.withInitCause(::EOFException)                    asStatus NOT_FOUND,
      ExceptionDescriptor.withInitCause(::FileNotFoundException)           asStatus NOT_FOUND,

    ).associateByTo(LinkedHashMap()) { it.exceptionDescriptor.type.java }.also { map ->
      val definedSupertypes = mutableSetOf<Class<*>>()
      for (throwableClass in map.keys) {
        check(throwableClass !in definedSupertypes) { "KNOWN_EXCEPTIONS should be from the most generic down to more specific" }
        definedSupertypes.addAll(throwableClass.superclassChain())
      }
    }
    // @formatter:on

    private infix fun <T : Throwable> ExceptionDescriptor<T>.asStatus(statusCode: Status.Code) =
      ExceptionAsStatus(statusCode.toStatus(), this)

    fun forThrowableClass(throwableClass: Class<*>): ExceptionAsStatus? {
      return throwableClass.superclassChain()
        .mapNotNull { KNOWN_EXCEPTIONS[it] }
        .firstOrNull()
    }

    private fun Class<*>.superclassChain() = generateSequence(this) { it.superclass }

    fun forStatusCode(statusCode: Status.Code): ExceptionAsStatus? {
      return KNOWN_EXCEPTIONS.values.firstOrNull { it.status.code == statusCode }
    }

    const val CLASS_NAME_START = "#"
    const val CLASS_NAME_DELIMITER = ": "

    inline fun <R> wrap(block: () -> R): R {
      return try {
        block()
      }
      catch (t: Throwable) {
        when (t) {
          is StatusException -> throw t
          is StatusRuntimeException -> throw t
        }
        val exceptionAsStatus = forThrowableClass(t.javaClass) ?: throw t
        throw exceptionAsStatus.status
          .withDescription(CLASS_NAME_START + t.javaClass.name + (t.message?.let { CLASS_NAME_DELIMITER + it } ?: ""))
          .withCause(t)
          .asException()
      }
    }

    inline fun <R> unwrap(block: () -> R): R {
      return try {
        block()
      }
      catch (e: Exception) {
        val status = when (e) {
          is StatusException -> e.status
          is StatusRuntimeException -> e.status
          else -> throw e
        }
        val code = status.code
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (code) {
          OK, UNKNOWN, INTERNAL, UNAUTHENTICATED -> throw e
        }
        val description = status.description

        val throwableClass =
          if (description != null && description.startsWith(CLASS_NAME_START)) {
            val className = description.substringAfter(CLASS_NAME_START).substringBefore(CLASS_NAME_DELIMITER)

            kotlin.runCatching {
              Class.forName(className, false, ExceptionAsStatus::class.java.classLoader)
            }.getOrNull()
          }
          else null

        val message = when {
          description == null -> code.toString()
          throwableClass == null -> description
          description.contains(CLASS_NAME_DELIMITER) -> description.substringAfter(CLASS_NAME_DELIMITER)
          else -> null
        }

        val exceptionAsStatus = throwableClass?.let { forThrowableClass(it) } ?: forStatusCode(code) ?: throw e
        throw exceptionAsStatus.exceptionDescriptor.constructor(message, e)
      }
    }
  }
}
