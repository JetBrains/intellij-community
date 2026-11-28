// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.ImageResourceLocation
import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.Serializable

/**
 * An image at [url], a `file:` URL or a `jar:` URL of a local archive (`jar:file:`), see
 * [com.intellij.platform.icons.design.IconDesigner.imageUrl].
 * Images are not loaded from the network, also not from a remote archive such as `jar:https:`. A `file:` URL with a host
 * is not accepted: on Windows, it is a network share.
 */
@Serializable
class UrlImageResourceLocation(val url: String) : ImageResourceLocation {
    init {
        require(isImageUrl(url)) {
            "Image location must be a file: URL without a host, or a jar: URL of such a file: URL: $url"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UrlImageResourceLocation

        return url == other.url
    }

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "UrlImageResourceLocation(url='$url')"
}

/**
 * Whether [url] is a valid `file:` URI without a host, or a `jar:` URI of an entry (`!/`) in an archive at such a `file:`
 * URI.
 */
private fun isImageUrl(url: String): Boolean {
    val uri = parseUri(url) ?: return false
    return when (uri.scheme) {
        "file" -> isLocalFileUri(uri)
        "jar" -> {
            val archiveAndEntry = uri.rawSchemeSpecificPart
            "!/" in archiveAndEntry &&
                parseUri(archiveAndEntry.substringBefore("!/"))?.let { it.scheme == "file" && isLocalFileUri(it) } == true
        }
        else -> false
    }
}

/**
 * Whether the `file:` [uri] is a local file. A host is a network share on Windows, and so is a network path (see
 * [isNetworkPath]), as in `file:////server/share` or `file:/%5C%5Cserver/share`. An opaque URI, such as `file:icon.svg`,
 * has no path.
 */
internal fun isLocalFileUri(uri: URI): Boolean =
    !uri.isOpaque && uri.authority.isNullOrEmpty() && !isNetworkPath(uri.path.orEmpty())

/**
 * Whether [path] is a network path: it starts with two separators, each `/` or `\`. Windows reads such a path as a UNC
 * path, for example `\\server\share`, `//server/share` or `/\server\share`.
 */
internal fun isNetworkPath(path: String): Boolean = path.length >= 2 && path[0].isPathSeparator() && path[1].isPathSeparator()

private fun Char.isPathSeparator(): Boolean = this == '/' || this == '\\'

private fun parseUri(text: String): URI? =
    try {
        URI(text)
    } catch (_: URISyntaxException) {
        null
    }
