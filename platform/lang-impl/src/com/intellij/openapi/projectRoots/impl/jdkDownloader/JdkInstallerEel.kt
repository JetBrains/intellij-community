// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.toNioPath
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

@Internal
object JdkInstallerEel {
  fun unpackJdkOnEel(
    eel: EelApi,
    downloadFile: Path,
    targetDirEel: EelPath.Absolute,
    packageRootPrefixRaw: String,
  ): Unit = runBlockingMaybeCancellable {
    var downloadFileEelCopy: EelPath.Absolute? = eel.mapper.getOriginalPath(downloadFile)

    val tempDirectory: EelPath.Absolute? =
      if (downloadFileEelCopy == null) {
        // TODO Eel downloading API
        val archiveName = EelPath.Relative.parse(downloadFile.name)

        downloadFileEelCopy = eel.fs
          .createTemporaryDirectory(EelFileSystemApi.CreateTemporaryDirectoryOptions.Builder().prefix("download-jdk-").build()).getOrThrow()
          .resolve(archiveName)

        Files.copy(downloadFile, downloadFileEelCopy.toNioPath(eel), StandardCopyOption.REPLACE_EXISTING)

        downloadFileEelCopy.parent
      }
      else null
    try {
      val unpackDir = targetDirEel.parent!!
        .resolve(EelPath.Relative.parse(".${targetDirEel.fileName}-downloading-${System.currentTimeMillis()}"))
      try {
        eel.archive.extract(downloadFileEelCopy, unpackDir)
        moveUnpackedJdkPrefixOnEel(
          eel = eel,
          unpackDir = unpackDir,
          targetDir = targetDirEel,
          packageRootPrefixRaw = packageRootPrefixRaw,
        )
      }
      finally {
        try {
          NioFiles.deleteRecursively(unpackDir.toNioPath(eel))
        }
        catch (_: FileSystemException) {
          // Ignored.
        }
      }
    }
    finally {
      tempDirectory?.let(eel.mapper::toNioPath)?.let { absolute ->
        NioFiles.deleteRecursively(absolute)
      }
    }
  }

  private suspend fun moveUnpackedJdkPrefixOnEel(
    eel: EelApi,
    unpackDir: EelPath.Absolute,
    targetDir: EelPath.Absolute,
    packageRootPrefixRaw: String,
  ) {
    val packageRootPrefix = packageRootPrefixRaw.removePrefix("./").trim('/')
    val packageRootResolved =
      if (packageRootPrefix.isBlank())
        unpackDir
      else
        unpackDir.resolve(EelPath.Relative.parse(packageRootPrefixRaw)).normalize()


    if (!packageRootResolved.startsWith(unpackDir)) {
      error("Failed to move JDK contents from $unpackDir to $packageRootResolved. Invalid metadata is detected")
    }

    if (!Files.isDirectory(packageRootResolved.toNioPath(eel))) {
      thisLogger().info("Could not unpack JDK in $packageRootResolved. File system entry is not a directory. ")
      return
    }
    eel.fs.move(packageRootResolved, targetDir, EelFileSystemApi.ReplaceExistingDuringMove.REPLACE_EVERYTHING, true)
  }
}