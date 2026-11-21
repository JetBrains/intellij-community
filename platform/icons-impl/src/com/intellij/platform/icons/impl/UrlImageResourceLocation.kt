// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.ImageResourceLocation
import java.nio.file.Path
import kotlinx.serialization.Serializable

/**
 * An image at [url], a `file:` or a `jar:` URL or a file path, see [com.intellij.platform.icons.design.IconDesigner.image].
 * A file path is kept as it is and resolved by [toUrl] where the image is loaded, so a relative path is resolved against
 * the working directory there. Images are not loaded from the network.
 */
@Serializable
class UrlImageResourceLocation(val url: String) : ImageResourceLocation {
    init {
        require(isUrl(url) || !hasScheme(url)) { "Image location must be a file: or a jar: URL or a file path: $url" }
    }

    /** [url] as a `file:` or a `jar:` URL; a file path is resolved on this machine. */
    fun toUrl(): String = if (isUrl(url)) url else Path.of(url).toUri().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UrlImageResourceLocation

        return url == other.url
    }

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "UrlImageResourceLocation(url='$url')"
}

private fun isUrl(url: String): Boolean = url.startsWith("file:") || url.startsWith("jar:")

/** Whether [url] starts with a URL scheme, such as `https:`; a Windows drive letter (`C:`) is not one. */
private fun hasScheme(url: String): Boolean {
    val colon = url.indexOf(':')
    if (colon < 2) return false
    val scheme = url.substring(0, colon)
    return scheme.first().isLetter() && scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
}
