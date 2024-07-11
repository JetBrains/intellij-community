package org.jetbrains.jewel.samples.standalone.reflection

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.samples.standalone.viewmodel.ViewInfo
import org.jetbrains.jewel.ui.icon.PathIconKey
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.reflect.jvm.kotlinFunction

internal fun findViews(packageName: String): List<ViewInfo> {
    val path = "/" + packageName.replace('.', '/').removePrefix("/")

    val uri =
        Class
            .forName("org.jetbrains.jewel.samples.standalone.reflection.ViewsKt")
            .getResource(path)
            ?.toURI() ?: return emptyList()

    val directory =
        if (uri.scheme == "jar") {
            val fileSystem =
                try {
                    FileSystems.getFileSystem(uri)
                } catch (_: FileSystemNotFoundException) {
                    FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                }
            fileSystem.getPath(path)
        } else {
            Paths.get(uri)
        }

    val result = mutableListOf<ViewInfo>()

    if (Files.exists(directory)) {
        Files
            .list(directory)
            .filter { f -> Files.isRegularFile(f) && !f.name.contains('$') && f.name.endsWith("Kt.class") }
            .forEach { f ->
                val fullyQualifiedClassName =
                    packageName +
                        f
                            .absolutePathString()
                            .removePrefix(directory.absolutePathString())
                            .dropLast(6) // remove .class
                            .replace('/', '.')
                try {
                    result +=
                        Class.forName(fullyQualifiedClassName).methods.mapNotNull {
                            val annotation = it.getAnnotation(View::class.java) ?: return@mapNotNull null
                            val kFunc = it.kotlinFunction ?: return@mapNotNull null
                            if (kFunc.parameters.isNotEmpty() || kFunc.returnType.classifier != Unit::class) return@mapNotNull null

                            @Suppress("UNCHECKED_CAST")
                            ViewInfo(
                                title = annotation.title,
                                position = annotation.position,
                                iconKey = PathIconKey(annotation.icon),
                                content = it.kotlinFunction as @Composable () -> Unit,
                            )
                        }
                } catch (e: ClassNotFoundException) {
                    JewelLogger.getInstance("Views").error(e)
                } catch (ignore: InstantiationException) {
                    // We try to instantiate an interface
                    // or an object that does not have a
                    // default constructor
                } catch (ignore: IllegalAccessException) {
                    // The class is not public
                }
            }
    }

    return result.sortedBy { it.position }
}
