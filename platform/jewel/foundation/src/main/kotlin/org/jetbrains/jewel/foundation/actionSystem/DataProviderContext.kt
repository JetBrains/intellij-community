package org.jetbrains.jewel.foundation.actionSystem

public interface DataProviderContext {
    public fun <TValue : Any> set(key: String, value: TValue?)

    public fun <TValue : Any> lazy(key: String, initializer: () -> TValue?)
}
