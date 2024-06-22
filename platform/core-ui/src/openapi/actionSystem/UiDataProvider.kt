// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer

@ApiStatus.OverrideOnly
fun interface UiDataProvider {
  /**
   * Override what is already in the sink or add new data.
   * Called in EDT.
   */
  @RequiresEdt
  fun uiDataSnapshot(sink: DataSink)
}

@ApiStatus.NonExtendable
interface DataSnapshot {

  operator fun <T : Any> get(key: DataKey<T>): T?
}

@ApiStatus.OverrideOnly
fun interface DataSnapshotProvider {

  fun dataSnapshot(sink: DataSink)
}

@ApiStatus.OverrideOnly
interface UiDataRule {
  /**
   * Adds what is missing in the sink.
   * Called in EDT and BGT (context customization in BGT).
   *
   * The snapshot contains all EDT data down to the current component (CONTEXT_COMPONENT).
   *
   * Rules must check CONTEXT_COMPONENT to avoid data duplication for all subcomponents.
   */
  fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot)

  @ApiStatus.Internal
  companion object {
    private val EP_NAME = ExtensionPointName.create<UiDataRule>("com.intellij.uiDataRule")

    @JvmStatic
    fun forEachRule(consumer: Consumer<in UiDataRule>) {
      EP_NAME.forEachExtensionSafe(consumer)
    }
  }
}

@ApiStatus.Obsolete
fun interface UiCompatibleDataProvider : UiDataProvider, DataProvider {

  @Deprecated("Migrate to [uiDataSnapshot] ASAP")
  override fun getData(dataId: @NonNls String): Any? {
    return null
  }
}

@ApiStatus.Obsolete
fun interface EdtNoGetDataProvider : DataSnapshotProvider, DataProvider {

  @ApiStatus.NonExtendable
  @Deprecated("Never called")
  override fun getData(dataId: @NonNls String): Any? {
    throw UnsupportedOperationException()
  }
}

@ApiStatus.NonExtendable
interface DataSink {

  operator fun <T : Any> set(key: DataKey<T>, data: T?)

  fun <T : Any> setNull(key: DataKey<T>)

  fun <T : Any> lazy(key: DataKey<T>, data: () -> T?)

  fun uiDataSnapshot(provider: UiDataProvider)

  fun dataSnapshot(provider: DataSnapshotProvider)

  /** Prefer [UiDataProvider] in UI code */
  @ApiStatus.Obsolete
  fun uiDataSnapshot(provider: DataProvider)

  companion object {
    private val LOG = Logger.getInstance(DataSink.javaClass)

    @ApiStatus.Obsolete
    @JvmStatic
    fun uiDataSnapshot(sink: DataSink, provider: DataProvider?) {
      uiDataSnapshot(sink, provider as Any?)
    }

    @ApiStatus.Obsolete
    @JvmStatic
    fun uiDataSnapshot(sink: DataSink, provider: Any?) {
      if (provider is CompositeDataProvider) {
        for (p in provider.dataProviders) {
          uiDataSnapshot(sink, p)
        }
      }
      else if (provider is DataSnapshotProvider) {
        sink.dataSnapshot(provider)
      }
      else {
        if (provider is UiDataProvider) {
          sink.uiDataSnapshot(provider)
        }
        if (provider is DataProvider) {
          sink.uiDataSnapshot(provider)
        }
        if (provider is Function1<*, *>) {
          LOG.error("Kotlin functions are not supported, use " +
                    "DataProvider/UiDataProvider/DataSnapshotProvider explicitly")
        }
      }
    }
  }
}