// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeWithMe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.Processor
import java.util.concurrent.Callable
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.jvm.JvmStatic

/**
 * ClientId is a global context class that is used to distinguish the originator of an action in multi-client systems
 * In such systems, each client has their own ClientId. Current process also can have its own ClientId, with this class providing methods to distinguish local actions from remote ones.
 *
 * It's up to the application to preserve and propagate the current value across background threads and asynchronous activities.
 */
data class ClientId(val value: String) {
    enum class AbsenceBehavior {
        /**
         * Return localId if ClientId is not set
         */
        RETURN_LOCAL,
        /**
         * Throw an exception if ClientId is not set
         */
        THROW
    }

    companion object {

        val logger = Logger.getInstance(ClientId::class.java)
        /**
         * Default client id for local application
         */
        val defaultLocalId = ClientId("Host")

        /**
         * Specifies behavior for ClientId.current
         */
        var AbsenceBehaviorValue = AbsenceBehavior.RETURN_LOCAL


        /**
         * Controls propagation behavior. When false, decorateRunnable does nothing.
         */
        var propagateAcrossThreads = false

        /**
         * The ID considered local to this process. All other IDs (except for null) are considered remote
         */
        @JvmStatic
        var localId = defaultLocalId
            get
            private set

        /**
         * True if and only if the current ClientID is local to this process
         */
        @JvmStatic
        val isCurrentlyUnderLocalId: Boolean
            get() = currentOrNull.isLocal

        /**
         * Gets the current ClientId. Subject to AbsenceBehaviorValue
         */
        @JvmStatic
        val current: ClientId
            get() = when (AbsenceBehaviorValue) {
                AbsenceBehavior.RETURN_LOCAL -> currentOrNull ?: localId
                AbsenceBehavior.THROW -> currentOrNull ?: throw NullPointerException("ClientId not set")
            }

        /**
         * Gets the current ClientId. Can be null if none was set.
         */
        @JvmStatic
        val currentOrNull: ClientId?
            get() = ClientIdValueStoreService.tryGetInstance()?.value?.let(::ClientId)

        /**
         * Overrides the ID that is considered to be local to this process. Can be only invoked once.
         */
        @JvmStatic
        fun overrideLocalId(newId: ClientId) {
            require(
                localId == defaultLocalId)
            localId = newId
        }

        /**
         * Returns true if and only if the given ID is considered to be local to this process
         */
        @JvmStatic
        fun isLocalId(clientId: ClientId?): Boolean {
            return clientId.isLocal
        }

        /**
         * Is true if and only if the given ID is considered to be local to this process
         */
        val ClientId?.isLocal: Boolean
            get() = this == null || this == localId

        /**
         * Invokes a runnable under the given ClientId
         */
        @JvmStatic
        fun withClientId(clientId: ClientId?, action: Runnable) = withClientId(clientId) { action.run() }

        /**
         * Computes a value under given ClientId
         */
        @JvmStatic
        fun <T> withClientId(clientId: ClientId?, action: Callable<T>): T = withClientId(clientId) { action.call() }

        /**
         * Computes a value under given ClientId
         */
        @JvmStatic
        inline fun <T> withClientId(clientId: ClientId?, action: () -> T): T {
            val clientIdStore = ClientIdValueStoreService.tryGetInstance() ?: return action()

            val foreignMainThreadActivity = ApplicationManager.getApplication().isDispatchThread && !clientId.isLocal
            val old = clientIdStore.value
            try {
                clientIdStore.value = clientId?.value
                if (foreignMainThreadActivity) {
                    val beforeActionTime = System.currentTimeMillis()
                    val result = action()
                    val delta = System.currentTimeMillis() - beforeActionTime
                    if (delta > 300) {
                        logger.warn("LONG MAIN THREAD ACTIVITY by ${clientId?.value}. Stack trace:\n${getStackTrace()}")
                    }
                    return result
                } else
                    return action()
            } finally {
                clientIdStore.value = old
            }
        }

        @JvmStatic
        fun decorateRunnable(runnable: java.lang.Runnable) : java.lang.Runnable {
            if (!propagateAcrossThreads) return runnable
            val currentId = currentOrNull
            return Runnable { withClientId(currentId, runnable) }
        }

        @JvmStatic
        fun <T> decorateCallable(callable: Callable<T>) : Callable<T> {
            if (!propagateAcrossThreads) return callable
            val currentId = currentOrNull
            return Callable { withClientId(currentId, callable) }
        }

        @JvmStatic
        fun <T, R> decorateFunction(function: Function<T, R>) : Function<T, R> {
            if (!propagateAcrossThreads) return function
            val currentId = currentOrNull
            return Function { withClientId(currentId) { function.apply(it) } }
        }

        @JvmStatic
        fun <T, U> decorateBiConsumer(biConsumer: BiConsumer<T, U>) : BiConsumer<T, U> {
            if (!propagateAcrossThreads) return biConsumer
            val currentId = currentOrNull
            return BiConsumer { t, u -> withClientId(currentId) { biConsumer.accept(t, u) } }
        }

        @JvmStatic
        fun <T> decorateProcessor(processor: Processor<T>) : Processor<T> {
            if (!propagateAcrossThreads) return processor
            val currentId = currentOrNull
            return Processor { withClientId(currentId) { processor.process(it) } }
        }
    }
}

fun isForeignClientOnServer(): Boolean {
    return !ClientId.isCurrentlyUnderLocalId && ClientId.localId == ClientId.defaultLocalId
}

fun getStackTrace(): String {
    val builder = StringBuilder()
    val trace = Thread.currentThread().stackTrace
    for (element in trace) {
        with(builder) { append("\tat $element\n") }
    }

    return builder.toString()
}