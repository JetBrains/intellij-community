// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window

import com.sun.jna.Native
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.measureTimeMillis
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

@ApiStatus.Internal
@InternalJewelApi
public object JnaLoader {
    private var loaded: Boolean? = null
    private val logger = Logger.getLogger(JnaLoader::class.java.simpleName)

    @Synchronized
    public fun load() {
        if (loaded == null) {
            loaded = false
            try {
                val time = measureTimeMillis { Native.POINTER_SIZE }
                logger.info("JNA library (${Native.POINTER_SIZE shl 3}-bit) loaded in $time ms")
                loaded = true
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                logger.log(
                    Level.WARNING,
                    "Unable to load JNA library(os=${
                        System.getProperty("os.name")
                    } ${System.getProperty("os.version")}, jna.boot.library.path=${
                        System.getProperty("jna.boot.library.path")
                    })",
                    t,
                )
            }
        }
    }

    @get:Synchronized
    public val isLoaded: Boolean
        get() {
            if (loaded == null) {
                load()
            }
            return loaded ?: false
        }
}
