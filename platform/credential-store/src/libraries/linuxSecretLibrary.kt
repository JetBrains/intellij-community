package com.intellij.credentialStore.linux

import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.LOG
import com.intellij.jna.DisposableMemory
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

private val LIBRARY by lazy { Native.loadLibrary("secret-1", SecretLibrary::class.java) as SecretLibrary }

private const val SECRET_SCHEMA_NONE = 0
private const val SECRET_SCHEMA_ATTRIBUTE_STRING = 0

// explicitly create pointer to be explicitly dispose it to avoid sensitive data in the memory
internal fun stringPointer(data: ByteArray): DisposableMemory {
  val pointer = DisposableMemory(data.size + 1L)
  pointer.write(0, data, 0, data.size)
  pointer.setByte(data.size.toLong(), 0.toByte())
  return pointer
}

// we use default collection, it seems no way to use custom
internal class SecretCredentialStore(schemeName: String) : CredentialStore {
  private val keyAttributeNamePointer by lazy { stringPointer("key".toByteArray()) }
  private val scheme by lazy { LIBRARY.secret_schema_new(schemeName, SECRET_SCHEMA_NONE, keyAttributeNamePointer, SECRET_SCHEMA_ATTRIBUTE_STRING, null) }

  override fun get(key: String): String? {
    val keyPointer = stringPointer(key.toByteArray())
    return checkError("secret_password_lookup_sync") { errorRef ->
      LIBRARY.secret_password_lookup_sync(scheme, null, errorRef, keyAttributeNamePointer, keyPointer, null)
    }
  }

  override fun set(key: String, password: ByteArray?) {
    val keyPointer = stringPointer(key.toByteArray())

    if (password == null) {
      checkError("secret_password_store_sync") { errorRef ->
        LIBRARY.secret_password_clear_sync(scheme, null, errorRef, keyAttributeNamePointer, keyPointer, null)
      }
      return
    }

    val passwordPointer = stringPointer(password)
    password.fill(0)

    checkError("secret_password_store_sync") { errorRef ->
      try {
        LIBRARY.secret_password_store_sync(scheme, null, keyPointer, passwordPointer, null, errorRef, keyAttributeNamePointer, keyPointer, null)
      }
      finally {
        passwordPointer.dispose()
        keyPointer.dispose()
      }
    }
  }
}

private inline fun <T> checkError(method: String, task: (errorRef: Array<GErrorStruct?>) -> T): T {
  val errorRef = arrayOf<GErrorStruct?>(null)
  val result = task(errorRef)
  val error = errorRef.get(0)
  if (error != null && error.code !== 0) {
    LOG.error("$method error code ${error.code}, error message ${error.message}")
  }
  return result
}

// we use sync API to simplify - client will use postponed write
private interface SecretLibrary : Library {
  fun secret_schema_new(name: String, flags: Int, vararg attributes: Any?): Pointer

  fun secret_password_store_sync(scheme: Pointer, collection: Pointer?, label: Pointer, password: Pointer, cancellable: Pointer?, error: Array<GErrorStruct?>, vararg attributes: Pointer?)

  fun secret_password_lookup_sync(scheme: Pointer, cancellable: Pointer?, error: Array<GErrorStruct?>, vararg attributes: Pointer?): String

  fun secret_password_clear_sync(scheme: Pointer, cancellable: Pointer?, error: Array<GErrorStruct?>, vararg attributes: Pointer?)
}

@Suppress("unused")
class GErrorStruct : com.sun.jna.Structure() {
  @JvmField
  var domain = 0
  @JvmField
  var code = 0
  @JvmField
  var message: String? = null

  override fun getFieldOrder() = listOf("domain", "code", "message")
}