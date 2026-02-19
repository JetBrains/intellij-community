package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import org.jetbrains.jewel.foundation.actionSystem.DataProviderContext

internal class DataProviderDataSinkContext(private val dataSink: DataSink) : DataProviderContext {
    override fun <TValue : Any> set(key: String, value: TValue?) {
        val ijKey = DataKey.create<TValue>(key)
        if (value == null) {
            dataSink.setNull(ijKey)
        }
        dataSink[ijKey] = value
    }

    override fun <TValue : Any> lazy(key: String, initializer: () -> TValue?) {
        val ijKey = DataKey.create<TValue>(key)
        dataSink.lazy(ijKey, initializer)
    }
}
