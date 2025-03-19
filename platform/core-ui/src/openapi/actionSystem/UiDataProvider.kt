// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A UI component implements [UiDataProvider] to add its UI state data to [DataContext].
 * It is a cleaner, faster and type-safe API than [DataProvider].
 *
 * [DataContext] consists of two parts: fast UI snapshot and slow BGT rules.
 *
 * 1. UI snapshot = layers of data from [UiDataProvider] or [DataProvider] (obsolete),
 * and a set of [UiDataRule]
 * 2. BGT rules = various [DataSink.lazy] and [PlatformCoreDataKeys.BGT_DATA_PROVIDER],
 * and a set of [com.intellij.ide.impl.dataRules.GetDataRule]
 */
@ApiStatus.OverrideOnly
fun interface UiDataProvider {
  /**
   * Override what is already in the sink or add new data.
   * Called in EDT.
   */
  @RequiresEdt
  fun uiDataSnapshot(sink: DataSink)

  companion object {
    /**
     * Use for simple cases to provide additional data.
     * In more complex cases provide your own component.
     */
    @JvmStatic
    fun wrapComponent(component: JComponent, provider: UiDataProvider): JComponent {
      return object : JPanel(BorderLayout()), UiDataProvider {
        init {
          add(component, BorderLayout.CENTER)
          isOpaque = component.isOpaque
        }

        override fun uiDataSnapshot(sink: DataSink) {
          DataSink.uiDataSnapshot(sink, provider)
        }
      }
    }
  }
}

/**
 * A data snapshot representation.
 * [UiDataRule] operates on it.
 */
@ApiStatus.NonExtendable
interface DataSnapshot {

  operator fun <T : Any> get(key: DataKey<T>): T?
}

/**
 * A non-EDT version of [UiDataProvider].
 * [com.intellij.openapi.actionSystem.CustomizedDataContext] takes it in
 * for [DataContext] customization in any thread.
 */
@ApiStatus.OverrideOnly
fun interface DataSnapshotProvider {

  /**
   * Override what is already in the sink or add new data.
   */
  fun dataSnapshot(sink: DataSink)
}

/**
 * A data rule to compute more UI data based on UI snapshot.
 * 1. Rules are non-recursive - rules cannot depend on other rules
 * 2. Rules cannot override existing data
 * Looking on a component hierarchy from top to bottom,
 * what is already in the snapshot can be modified only by a component data provider on a deeper level
 * 3. Despite the name, rules can be called in BGT to customize UI snapshot of a [DataContext]
 */
@ApiStatus.OverrideOnly
interface UiDataRule {
  /**
   * Add what is missing in the sink.
   * The snapshot = layers of data from [UiDataProvider] or [DataProvider] (obsolete).
   *
   * 1. Called in EDT after building UI snapshot.
   * The snapshot provides [PlatformCoreDataKeys.CONTEXT_COMPONENT]
   * 2. Called in BGT when customizing [DataContext].
   * The snapshot does not provide [PlatformCoreDataKeys.CONTEXT_COMPONENT]
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

/**
 * A migration tool when [DataProvider.getData] API is required *and used*
 */
@ApiStatus.Obsolete
fun interface UiCompatibleDataProvider : UiDataProvider, DataProvider {

  @Deprecated("Migrate to [uiDataSnapshot] ASAP")
  override fun getData(dataId: @NonNls String): Any? {
    return null
  }
}

/**
 * A migration tool when [DataProvider.getData] API is required *but must never be used*
 */
@ApiStatus.Obsolete
fun interface EdtNoGetDataProvider : DataSnapshotProvider, DataProvider {

  @ApiStatus.NonExtendable
  @Deprecated("Never called")
  override fun getData(dataId: @NonNls String): Any? {
    throw UnsupportedOperationException()
  }
}

/**
 * API to accommodate data from various sources in a type-safe way
 */
@ApiStatus.NonExtendable
interface DataSink {

  /**
   * Put the data in the sink
   */
  operator fun <T : Any> set(key: DataKey<T>, data: T?)

  /**
   * Put the [com.intellij.openapi.actionSystem.CustomizedDataContext.EXPLICIT_NULL] value in the sink
   */
  fun <T : Any> setNull(key: DataKey<T>)

  /**
   * Put the [PlatformCoreDataKeys.BGT_DATA_PROVIDER] lambda in the sink
   */
  fun <T : Any> lazy(key: DataKey<T>, data: () -> T?)

  /**
   * Put the [com.intellij.openapi.actionSystem.CustomizedDataContext.EXPLICIT_NULL] value in the sink
   * when it is its turn to provide a value.
   */
  fun <T : Any> lazyNull(key: DataKey<T>)

  /**
   * Put all data from the [provider] in the sink.
   * When migrating code, consider [DataSink.Companion.uiDataSnapshot] instead.
   */
  fun uiDataSnapshot(provider: UiDataProvider)

  /**
   * Put all data from the [provider] in the sink
   */
  fun dataSnapshot(provider: DataSnapshotProvider)

  /**
   * 1. Consider using [UiDataProvider] in UI code
   * 2. Call [DataSink.Companion.uiDataSnapshot] instead
   */
  @ApiStatus.Obsolete
  @ApiStatus.OverrideOnly
  fun uiDataSnapshot(provider: DataProvider)

  companion object {
    private val LOG = Logger.getInstance(DataSink.javaClass)

    /**
     * A migration tool when [DataProvider.getData] API is still used
     */
    @ApiStatus.Obsolete
    @JvmStatic
    fun uiDataSnapshot(sink: DataSink, provider: DataProvider?) {
      uiDataSnapshot(sink, provider as Any?)
    }

    /**
     * A migration tool when [DataProvider.getData] API is still used
     */
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
                    "UiDataProvider/DataSnapshotProvider/DataProvider explicitly")
        }
      }
    }
  }
}