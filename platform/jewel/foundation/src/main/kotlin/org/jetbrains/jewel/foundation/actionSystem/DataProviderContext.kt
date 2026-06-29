package org.jetbrains.jewel.foundation.actionSystem

/** A context used to supply data entries to the IntelliJ Platform action system from within a Compose component. */
public interface DataProviderContext {
    /** Registers an eager data [value] for the given [key] in the action system context. */
    public fun <TValue : Any> set(key: String, value: TValue?)

    /** Registers a lazily-initialized value for the given [key]; [initializer] is invoked on first access. */
    public fun <TValue : Any> lazy(key: String, initializer: () -> TValue?)
}
