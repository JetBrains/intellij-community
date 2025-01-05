@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import org.jetbrains.jps.incremental.relativizer.PathRelativizer
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val BASE_ID_PREFIX = "#"
private const val OUT_ID_PREFIX = "@"

internal fun createPathRelativizer(baseDir: Path, classOutDir: Path): PathRelativizerService {
  val baseDirPrefix = "${baseDir.invariantSeparatorsPathString}/"
  // Bazel may use paths with `../`
  val parentOfBaseDirPrefix = "${baseDir.parent.invariantSeparatorsPathString}/"
  val outBaseDirPrefix = "${classOutDir.invariantSeparatorsPathString}/"

  val typeAwareRelativizer = object : PathTypeAwareRelativizer {
    override fun toRelative(path: String, type: RelativePathType): String {
      val p = path.replace(File.separatorChar, '/')
      when (type) {
        RelativePathType.SOURCE -> {
          return when {
            p.startsWith(baseDirPrefix) -> p.substring(baseDirPrefix.length)
            p.startsWith(parentOfBaseDirPrefix) -> "../" + p.substring(parentOfBaseDirPrefix.length)
            else -> error("Unexpected path: $p")
          }
        }
        RelativePathType.OUTPUT -> {
          require(p.startsWith(outBaseDirPrefix)) { "Unexpected path: $p" }
          return p.substring(outBaseDirPrefix.length)
        }
      }
    }

    override fun toRelative(path: Path, type: RelativePathType): String {
      return toRelative(path.invariantSeparatorsPathString, type)
    }

    override fun toAbsolute(path: String, type: RelativePathType): String {
      return when (type) {
        RelativePathType.SOURCE -> baseDirPrefix + path
        RelativePathType.OUTPUT -> outBaseDirPrefix + path
      }
    }
  }

  return PathRelativizerService(arrayOf(object : PathRelativizer {
    override fun toRelativePath(path: String): String? {
      return when {
        path.startsWith(outBaseDirPrefix) -> OUT_ID_PREFIX + path.substring(outBaseDirPrefix.length)
        path.startsWith(baseDirPrefix) -> BASE_ID_PREFIX + path.substring(baseDirPrefix.length)
        else -> null
      }
    }

    override fun toAbsolutePath(path: String): String? {
      return when {
        path.startsWith(OUT_ID_PREFIX) -> outBaseDirPrefix + path.substring(OUT_ID_PREFIX.length)
        path.startsWith(BASE_ID_PREFIX) -> baseDirPrefix + path.substring(BASE_ID_PREFIX.length)
        else -> null
      }
    }
  }), typeAwareRelativizer)
}
