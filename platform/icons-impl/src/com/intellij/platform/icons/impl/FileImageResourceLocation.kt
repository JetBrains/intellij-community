// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.ImageResourceLocation
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlinx.serialization.Serializable

/**
 * An image file at [path], see [com.intellij.platform.icons.design.IconDesigner.imageFile]. The path is kept as it is, also
 * a relative one, so that it means the same on another machine, for example the frontend in remote development. It uses
 * `/` as the separator on each OS. It is resolved only when the image is loaded, see [toUrl].
 *
 * A relative path is resolved against the working directory of the process that loads the image. An absolute path of
 * one OS can mean nothing on another OS. The image is then not drawn.
 */
@Serializable
class FileImageResourceLocation(val path: String) : ImageResourceLocation {
    /**
     * The `file:` URL of [path], resolved against the working directory of this process when [path] is relative.
     * It is `null` when [path] is not a valid path on this OS. It is also `null` for a path on a network share, for
     * example a UNC path on Windows. Images are not loaded from the network.
     */
    fun toUrl(): String? {
        // checked before the path is read: on Windows, `toUri` reads the file attributes, also of a network share
        if (isNetworkPath(path)) return null
        val uri =
            try {
                Path.of(path).toAbsolutePath().normalize().toUri()
            } catch (_: InvalidPathException) {
                return null
            }
        return if (isLocalFileUri(uri)) uri.toString() else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileImageResourceLocation

        return path == other.path
    }

    override fun hashCode(): Int = path.hashCode()

    override fun toString(): String = "FileImageResourceLocation(path='$path')"
}
