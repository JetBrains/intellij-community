package org.jetbrains.jewel.util.font

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

fun Flow<File>.asFileProviderFlow(origin: FileProvider.Origin) =
    map { FileProvider(it.name, it.extension, it.absolutePath, origin) { it } }

data class FileProvider(
    val name: String,
    val extension: String,
    val path: String,
    val origin: Origin,
    val provider: () -> File
) {

    enum class Origin {
        SYSTEM_API,
        FILESYSTEM,
        CLASSPATH,
        RESOURCES,
        OTHER
    }
}
