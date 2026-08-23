// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

/**
 * Describes a piece of diff user data that [FrontendDiffExtension] may read on the frontend, and how to transfer it there.
 *
 * The data is put under [key] into `DiffContext` or `DiffRequest` by whoever shows the diff, and is read back via
 * [FrontendDiffExtensionData]. In split mode it travels between the backend and the frontend as [write]/[read] bytes,
 * so the on-the-wire format is up to the descriptor: implement [read] so that it also accepts the bytes written by the
 * versions of this descriptor that the counterpart IDE may still be running. For the common case see
 * [JsonFrontendDiffUserDataKeyDescriptor].
 */
@ApiStatus.Internal
interface FrontendDiffUserDataKeyDescriptor<T : Any> {
  /** Identifies the data on the wire, so it must stay stable between versions - unlike the name of [key], which is a debug name. */
  val id: String

  val key: Key<T>

  /** Throws when [bytes] cannot be read - the caller reports the failure and skips the value. */
  fun read(bytes: ByteArray): T

  fun write(value: T): ByteArray

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FrontendDiffUserDataKeyDescriptor<*>> =
      ExtensionPointName.create("com.intellij.diff.frontendUserDataKey")

    /** Every registered descriptor for which [holder] has a value, paired with that value. */
    fun collect(holder: UserDataHolder): List<FrontendDiffUserDataValue<*>> =
      EP_NAME.extensionList.mapNotNull { descriptor -> descriptor.valueOf(holder) }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> find(key: Key<T>): FrontendDiffUserDataKeyDescriptor<T>? =
      EP_NAME.extensionList.firstOrNull { descriptor -> descriptor.key == key } as? FrontendDiffUserDataKeyDescriptor<T>

    private fun <T : Any> FrontendDiffUserDataKeyDescriptor<T>.valueOf(holder: UserDataHolder): FrontendDiffUserDataValue<T>? =
      holder.getUserData(key)?.let { FrontendDiffUserDataValue(this, it) }
  }
}

/** A value together with the descriptor it belongs to, which keeps the two type-compatible without exposing the value type. */
@ApiStatus.Internal
data class FrontendDiffUserDataValue<T : Any>(
  val descriptor: FrontendDiffUserDataKeyDescriptor<T>,
  val value: T,
) {
  fun write(): ByteArray = descriptor.write(value)
}

/**
 * A descriptor that transfers its value as JSON, which fits any [kotlinx.serialization.Serializable] type.
 *
 * Unknown properties are ignored on reading, so a field added on one side does not break an IDE that runs an older
 * version of [T]; any other change of [T] still has to stay readable by both sides, or be made under a new [id].
 */
@ApiStatus.Internal
abstract class JsonFrontendDiffUserDataKeyDescriptor<T : Any>(
  override val id: String,
  override val key: Key<T>,
  private val serializer: KSerializer<T>,
) : FrontendDiffUserDataKeyDescriptor<T> {
  override fun read(bytes: ByteArray): T = JSON.decodeFromString(serializer, bytes.decodeToString())

  override fun write(value: T): ByteArray = JSON.encodeToString(serializer, value).encodeToByteArray()

  private companion object {
    private val JSON = Json { ignoreUnknownKeys = true }
  }
}
