package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.*

internal class TestDataSink : DataSink {
    val allData = mutableMapOf<String, Any>()

    fun clear() {
        allData.clear()
    }

    inline fun <reified T> get(key: String): T? {
        val data = allData[key] as T?
        if (data is T) return data
        return null
    }

    override fun dataSnapshot(provider: DataSnapshotProvider) {
        provider.dataSnapshot(this)
    }

    override fun uiDataSnapshot(provider: DataProvider) {
        // NOT needed in current tests
    }

    override fun uiDataSnapshot(provider: UiDataProvider) {
        provider.uiDataSnapshot(this)
    }

    override fun <T : Any> setNull(key: DataKey<T>) {
        allData.remove(key.name)
    }

    override fun <T : Any> set(key: DataKey<T>, data: T?) {
        if (data != null) {
            allData[key.name] = data
        }
    }

    override fun <T : Any> lazy(key: DataKey<T>, data: () -> T?) {
        set(key, data())
    }

    override fun <T : Any> lazyNull(key: DataKey<T>) {
        set(key, null)
    }

    override fun <T : Any> lazyValue(key: DataKey<T>, data: (DataMap) -> T?) {
    }
}
