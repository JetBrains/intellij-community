// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.util.io.DataInputOutputUtil
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.function.Function
import java.util.function.IntFunction
import java.util.function.ToIntFunction
import kotlin.Throws

abstract class FilePropertyKeyImpl<T, RAW> protected constructor(name: String,
                                                                 private val persistentAttribute: FileAttribute) : FilePropertyKey<T> {
  @get:VisibleForTesting
  val userDataKey: Key<Any?> = Key.create(name)

  @Contract("null -> null")
  override fun getPersistentValue(virtualFile: VirtualFile?): T? {
    if (virtualFile == null) return null
    val raw = getRaw(virtualFile)
    return raw?.let { fromRaw(it) }
  }

  private fun getRaw(virtualFile: VirtualFile): RAW? {
    @Suppress("UNCHECKED_CAST")
    val memValue = virtualFile.getUserData(userDataKey) as RAW?
    if (memValue != null) {
      return if (memValue === NULL_MARKER) null else memValue
    }

    val persisted = readValue(virtualFile)
    // Value in virtualFile can be null only before the very first assignment,
    // so we can be sure that we don't overwrite a value of a set operation that happened in the middle of get operation.
    virtualFile.replace(userDataKey, null, persisted ?: NULL_MARKER)
    return persisted
  }

  override fun setPersistentValue(virtualFile: VirtualFile?, newValue: T?): Boolean {
    if (virtualFile == null) return false
    val oldValue = getRaw(virtualFile)
    val rawNewValue = newValue?.let { toRaw(it) }
    if (keysEqual(oldValue, rawNewValue)) {
      return false
    }
    else {
      writeValue(virtualFile, rawNewValue)
      if (!virtualFile.replace(userDataKey, oldValue ?: NULL_MARKER, if (newValue == null) NULL_MARKER else rawNewValue)) {
        // Concurrent update is not supported.
        // At this point it's not known in which order calls to writeValue were executed and which value was actually persisted to disk.
        // And therefore we don't know which value should be written to VirtualFile.

        // todo: Check why concurrent update happens and uncomment the LOG.error()
        //       Check SqlImportStateTest, SqlJoinCardinalityInlayTest
        //       But in general this doesn't seem to lead to problems because value is updated to same same value concurrently,
        //       so result is deterministic.
        //LOG.error("Value for key $userDataKey in file $virtualFile is updated concurrently. Trying to update it from $oldValue to $rawNewValue. " +
        //          "Current value is ${getRaw(virtualFile)}")
      }
      return true
    }
  }

  private fun keysEqual(k1: RAW?, k2: RAW?): Boolean = (k1 == k2)

  protected fun readValue(virtualFile: VirtualFile): RAW? {
    if (virtualFile !is VirtualFileWithId) {
      LOG.debug("Only VirtualFileWithId can have persistent attributes: $virtualFile")
      return null
    }
    try {
      persistentAttribute.readFileAttribute(virtualFile).use { stream ->
        if (stream != null && stream.available() > 0) {
          return readValue(stream)
        }
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return null
  }

  protected fun writeValue(virtualFile: VirtualFile, newValue: RAW?) {
    if (virtualFile !is VirtualFileWithId) {
      LOG.debug("Only VirtualFileWithId can have persistent attributes: $virtualFile")
      return
    }
    try {
      persistentAttribute.writeFileAttribute(virtualFile).use { stream -> writeValue(stream, newValue) }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  @Throws(IOException::class)
  protected abstract fun readValue(stream: AttributeInputStream): RAW?

  @Throws(IOException::class)
  protected abstract fun writeValue(stream: AttributeOutputStream, newValue: RAW?)

  /*
   * Note that we can write something meaningful into persistent attribute (e.g. LanguageID="GenericSQL"), then user may remove the plugin,
   * and stored value may become meaningless. In this case it is OK to transform non-null into null value
   */
  protected abstract fun fromRaw(value: RAW): T?
  protected abstract fun toRaw(value: T): RAW

  companion object {

    @JvmStatic
    private val NULL_MARKER by lazy(LazyThreadSafetyMode.PUBLICATION) {
      if (Registry.`is`("cache.nulls.for.pushed.properties", true)) {
        Object()
      }
      else {
        null
      }
    }

    @JvmStatic
    private val LOG = Logger.getInstance(FilePropertyKeyImpl::class.java)

    @JvmStatic
    fun createPersistentStringKey(name: String, persistentAttribute: FileAttribute): FilePropertyKey<String> {
      return createPersistentStringKey(name, persistentAttribute, String::toString, String::toString)
    }

    @JvmStatic
    fun <T> createPersistentStringKey(name: String,
                                      persistentAttribute: FileAttribute,
                                      fnToRaw: Function<T, String>,
                                      fnFromRaw: Function<String, T?>): FilePropertyKey<T> {
      return FilePropertyStringKey(name, persistentAttribute, fnToRaw, fnFromRaw)
    }

    @JvmStatic
    fun createPersistentIntKey(userDataName: String,
                               persistentDataName: String,
                               persistentDataVersion: Int): FilePropertyKey<Int> {
      return FilePropertyIntKey(userDataName, FileAttribute(persistentDataName, persistentDataVersion, true), { t -> t }, { t -> t })
    }

    @JvmStatic
    fun createPersistentBooleanKey(userDataName: String, persistentDataName: String, persistentDataVersion: Int): FilePropertyKey<Boolean> {
      return FilePropertyIntKey(userDataName, FileAttribute(persistentDataName, persistentDataVersion, true), { t -> if (t) 1 else 0 }, { t -> t != 0 })
    }

    @JvmStatic
    fun <T : Enum<T>> createPersistentEnumKey(userDataName: String,
                                              persistentDataName: String,
                                              persistentDataVersion: Int,
                                              clazz: Class<T>): FilePropertyKey<T> {
      fun fromRaw(value: Int): T? {
        if (value >= 0 && value < clazz.enumConstants.size) {
          return clazz.enumConstants[value]
        }
        else {
          return null
        }
      }

      fun toRaw(value: T): Int = value.ordinal

      return FilePropertyIntKey(userDataName, FileAttribute(persistentDataName, persistentDataVersion, true),
                                { t -> toRaw(t) }, { t -> fromRaw(t) })
    }
  }
}

internal class FilePropertyStringKey<T>(name: String, persistentAttribute: FileAttribute,
                                        private val fnToRaw: Function<T, String>,
                                        private val fnFromRaw: Function<String, T?>) : FilePropertyKeyImpl<T, String>(name,
                                                                                                                      persistentAttribute) {
  override fun fromRaw(value: String): T? = fnFromRaw.apply(value)

  override fun toRaw(value: T): String = fnToRaw.apply(value)

  @Throws(IOException::class)
  override fun readValue(stream: AttributeInputStream): String? = stream.readEnumeratedString()

  @Throws(IOException::class)
  override fun writeValue(stream: AttributeOutputStream, newValue: String?) = stream.writeEnumeratedString(newValue)
}

internal class FilePropertyIntKey<T>(name: String,
                                     persistentAttribute: FileAttribute,
                                     private val fnToRaw: ToIntFunction<T>,
                                     private val fnFromRaw: IntFunction<T?>) : FilePropertyKeyImpl<T, Int>(name,
                                                                                                           persistentAttribute) {
  @Throws(IOException::class)
  override fun readValue(stream: AttributeInputStream): Int = DataInputOutputUtil.readINT(stream)

  @Throws(IOException::class)
  override fun writeValue(stream: AttributeOutputStream, newValue: Int?) {
    newValue?.let { DataInputOutputUtil.writeINT(stream, it) }
  }

  override fun fromRaw(value: Int): T? = fnFromRaw.apply(value)
  override fun toRaw(value: T): Int = fnToRaw.applyAsInt(value)
}