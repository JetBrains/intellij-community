// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.bundle

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Enumeration
import java.util.Locale
import java.util.Properties
import java.util.ResourceBundle

internal object JewelResourceControl : ResourceBundle.Control() {
    override fun getFormats(baseName: String): List<String> = FORMAT_PROPERTIES

    override fun newBundle(
        baseName: String,
        locale: Locale,
        format: String,
        loader: ClassLoader,
        reload: Boolean,
    ): ResourceBundle? {
        val bundleName = toBundleName(baseName, locale)
        // application protocol check
        val resourceName =
            if (bundleName.contains("://")) {
                return null
            } else {
                toResourceName(bundleName, "properties") ?: return null
            }

        val stream = loader.getResourceAsStream(resourceName) ?: return null
        return stream.use { JewelResourceBundle(reader = InputStreamReader(it, StandardCharsets.UTF_8)) }
    }
}

internal class MissingResourceBundle(private val baseName: String) : ResourceBundle() {
    override fun handleGetObject(key: String): Any? = null

    override fun getKeys(): Enumeration<String> = Collections.emptyEnumeration()

    override fun getBaseBundleName(): String = baseName
}

/**
 * Loads the content from the given input stream as a [Properties] type.
 *
 * **Swing Equivalent:** [com.intellij.IntelliJResourceBundle]
 */
internal class JewelResourceBundle(reader: InputStreamReader) : ResourceBundle() {
    private val lookup: Map<String, String>

    init {
        val properties = Properties()
        properties.load(reader)
        // don't use immutable map - HashMap performance is better (with String keys)
        @Suppress("UNCHECKED_CAST")
        lookup = HashMap(properties as Map<String, String>)
    }

    fun getMessageOrNull(key: String): String? = lookup[key]

    fun getMessage(key: String, params: Array<out Any?>?): String {
        var value = lookup[key]

        if (value == null) {
            // "!$key!" is the default handling for missing keys
            // The official IJ implementation also logs an error. We can add something similar
            value = DynamicBundle.defaultValue(key)
        }

        return DynamicBundle.postprocessValue(bundle = this, originalValue = value, params = params)
    }

    // UI Designer uses ResourceBundle directly, via Java API.
    // `getMessage` is not called, so we have to provide our own implementation of ResourceBundle
    override fun handleGetObject(key: String): String? = getMessageOrNull(key = key)

    override fun getKeys(): Enumeration<String> {
        val parent = super.parent
        return if (parent == null) {
            Collections.enumeration(lookup.keys)
        } else {
            ResourceBundleWithParentEnumeration(lookup.keys, parent.keys)
        }
    }

    override fun handleKeySet(): Set<String> = lookup.keys
}

/**
 * Creates an enumeration with current (set) + parent (enumeration) values
 *
 * **Swing Equivalent:** [com.intellij.ResourceBundleWithParentEnumeration]
 */
private class ResourceBundleWithParentEnumeration(
    private val set: Set<String?>,
    private val enumeration: Enumeration<String>,
) : Enumeration<String> {
    private val iterator: Iterator<String?> = set.iterator()
    private var next: String? = null

    @Suppress("NestedBlockDepth") // Copied form the IDE sources
    override fun hasMoreElements(): Boolean {
        if (next == null) {
            if (iterator.hasNext()) {
                next = iterator.next()
            } else {
                while (next == null && enumeration.hasMoreElements()) {
                    next = enumeration.nextElement()
                    if (set.contains(next)) {
                        next = null
                    }
                }
            }
        }
        return next != null
    }

    override fun nextElement(): String? {
        if (hasMoreElements()) {
            val result = next
            next = null
            return result
        } else {
            throw NoSuchElementException()
        }
    }
}
