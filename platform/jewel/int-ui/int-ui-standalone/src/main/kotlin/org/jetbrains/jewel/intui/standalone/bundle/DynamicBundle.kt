// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.bundle

import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.MessageFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.math.abs
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.skiko.hostOs

private const val MNEMONIC: Char = 0x1B.toChar()

/**
 * Used to load localized Strings/Messages from the resources dir.
 *
 * This class is a simplified version of the [com.intellij.ide.DynamicBundle], but supporting all core features like
 * formatting, and loading 'properties' files from the resources.
 *
 * Example:
 * ```properties, file=src/main/resources/messages/JewelIntUIBundle.properties
 * action.text.more=More
 * ```
 * ```kotlin, file=src/main/kotlin/org/jetbrains/jewel/intui/standalone/JewelIntUIBundle.kt
 * public object JewelIntUIBundle : DynamicBundle(
 *      JewelIntUIBundle::class.java,
 *      "messages.JewelIntUIBundle"
 * )
 * ```
 *
 * @see org.jetbrains.jewel.intui.standalone.JewelIntUIBundle
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public open class DynamicBundle(bundleClass: Class<*>, private val pathToBundle: String) {
    private val bundleClassLoader = bundleClass.classLoader
    private var bundle: Reference<ResourceBundle>? = null

    private val cache = ConcurrentHashMap<String, ResourceBundle>()

    public fun getMessage(key: @NonNls String, vararg params: Any?): @Nls String =
        @Suppress("HardCodedStringLiteral") getResourceBundle().messageOrDefault(key, params = params)

    public fun getLazyMessage(key: @NonNls String, vararg params: Any?): Supplier<@Nls String> = Supplier {
        getMessage(key, params = params)
    }

    internal fun getResourceBundle(): ResourceBundle {
        var bundle = this.bundle?.get()

        if (bundle == null) {
            bundle = resolveResourceBundleWithFallback()
            val ref = SoftReference(bundle)
            this.bundle = ref
        }

        return bundle
    }

    private fun resolveResourceBundleWithFallback(): ResourceBundle =
        try {
            cache.computeIfAbsent(pathToBundle) {
                ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), bundleClassLoader, JewelResourceControl)
            }
        } catch (_: MissingResourceException) {
            try {
                ResourceBundle.clearCache(bundleClassLoader)
                ResourceBundle.getBundle(pathToBundle, Locale.getDefault(), bundleClassLoader)
            } catch (_: MissingResourceException) {
                MissingResourceBundle(pathToBundle)
            }
        }

    public companion object {
        @Suppress("HardCodedStringLiteral")
        internal fun postprocessValue(
            bundle: ResourceBundle,
            originalValue: @Nls String,
            params: Array<out Any?>?,
        ): @Nls String {
            val value = replaceMnemonicAmpersand(originalValue)
            if (params.isNullOrEmpty() || !value.contains('{')) {
                return value
            }

            val locale = bundle.locale
            try {
                val format = if (locale == null) MessageFormat(value) else MessageFormat(value, locale)
                OrdinalFormat.apply(format)
                return format.format(params)
            } catch (_: IllegalArgumentException) {
                return "!invalid format: `$value`!"
            }
        }

        internal fun defaultValue(key: String): String = "!$key!"
    }
}

private fun ResourceBundle.messageOrDefault(key: String, params: Array<out Any?>?) =
    when (this) {
        is JewelResourceBundle -> this.getMessage(key, params = params)
        else -> runCatching { getString(key) }.getOrElse { DynamicBundle.defaultValue(key) }
    }

@Suppress("NestedBlockDepth") // Copied from IDE sources
private fun replaceMnemonicAmpersand(value: @Nls String): @Nls String {
    if (!value.contains('&') || value.contains(MNEMONIC)) {
        return value
    }

    val builder = StringBuilder()
    val macMnemonic = value.contains("&&")
    var mnemonicAdded = false
    var i = 0
    while (i < value.length) {
        when (val c = value[i]) {
            '\\' -> {
                if (i < value.length - 1 && value[i + 1] == '&') {
                    builder.append('&')
                    i++
                } else {
                    builder.append(c)
                }
            }

            '&' -> {
                if (i < value.length - 1 && value[i + 1] == '&') {
                    if (hostOs.isMacOS && !mnemonicAdded) {
                        mnemonicAdded = true
                        builder.append(MNEMONIC)
                    }
                    i++
                } else if (!hostOs.isMacOS || !macMnemonic) {
                    if (!mnemonicAdded) {
                        mnemonicAdded = true
                        builder.append(MNEMONIC)
                    }
                }
            }

            else -> {
                builder.append(c)
            }
        }
        i++
    }
    @Suppress("HardCodedStringLiteral")
    return builder.toString()
}

/**
 * Format numbers in the correct ordinal format.
 *
 * e.g.: 1 -> 1st, 2 -> 2nd, 8 -> 8th
 *
 * **Swing Equivalent:** [com.intellij.util.text.OrdinalFormat]
 */
private object OrdinalFormat {
    /** Replaces all instances of `"{?,number,ordinal}"` format elements with the ordinal format for the locale. */
    fun apply(format: MessageFormat) {
        val formats = format.getFormats()
        var ordinal: NumberFormat? = null
        for (i in formats.indices) {
            val element = formats[i]
            if (element is DecimalFormat && "ordinal" == element.positivePrefix) {
                if (ordinal == null) ordinal = getOrdinalFormat(format.locale)
                format.setFormat(i, ordinal)
            }
        }
    }

    private fun getOrdinalFormat(locale: Locale?): NumberFormat {
        if (locale != null) {
            val language = locale.language
            if ("en" == language || language.isEmpty()) {
                return EnglishOrdinalFormat()
            }
        }

        return DecimalFormat()
    }

    fun formatEnglish(num: Long): String {
        var mod = abs(num) % 100
        if (mod !in 11..13) {
            mod %= 10
            if (mod == 1L) return num.toString() + "st"
            if (mod == 2L) return num.toString() + "nd"
            if (mod == 3L) return num.toString() + "rd"
        }
        return num.toString() + "th"
    }

    private class EnglishOrdinalFormat : NumberFormat() {
        override fun format(number: Long, toAppendTo: StringBuffer, pos: FieldPosition?): StringBuffer? =
            MessageFormat("{0}").format(arrayOf<Any>(formatEnglish(number)), toAppendTo, pos)

        override fun format(number: Double, toAppendTo: StringBuffer?, pos: FieldPosition?): StringBuffer =
            throw java.lang.IllegalArgumentException("Cannot format non-integer number")

        override fun parse(source: String?, parsePosition: ParsePosition?): Number =
            throw UnsupportedOperationException()
    }
}
