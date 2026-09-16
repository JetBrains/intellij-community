package org.jetbrains.intellij.build.impl

import com.intellij.util.io.Compressor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.io.zip
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
fun writeResourceArchiveImpl(source: Path, target: Path): Path {
  if (Files.isDirectory(source)) {
    zip(targetFile = target, dirs = mapOf(source to ""))
    return target
  }
  val archive = target.resolve(source.fileName)
  Compressor.Zip(archive).use { it.addFile(archive.fileName.toString(), source) }
  return archive
}
