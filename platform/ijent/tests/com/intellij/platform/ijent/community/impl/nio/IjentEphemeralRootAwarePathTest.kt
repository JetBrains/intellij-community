// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.ijent.community.impl.ijentFailSafeFileSystemApi
import com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwareFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwarePath
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.coroutines.CoroutineContext

internal class IjentEphemeralRootAwarePathTest {
  @TestFactory
  fun `mapped Windows paths preserve their URI`() = listOf("@/C/project", "server/share/project").map { suffix ->
    DynamicTest.dynamicTest(suffix) {
      val mount = Path.of("ijent-path-test").toAbsolutePath()
      fileSystem(EelOsFamily.Windows, mount).use { fs ->
        val localPath = mount.resolve(suffix)
        assertEquals(localPath.toUri(), fs.getPath(localPath.toString()).toUri())
      }
    }
  }

  @TestFactory
  fun `absolute paths keep their environment when resolved`() = listOf(
    EelOsFamily.Posix to "project",
    EelOsFamily.Windows to "@/C/project",
    EelOsFamily.Windows to "server/share/project",
  ).map { (osFamily, suffix) ->
    DynamicTest.dynamicTest("$osFamily: $suffix") {
      val sourceMount = Path.of("ijent-source-test").toAbsolutePath()
      val targetMount = Path.of("ijent-target-test").toAbsolutePath()
      fileSystem(osFamily, sourceMount).use { sourceFs ->
        fileSystem(osFamily, targetMount).use { targetFs ->
          val source = sourceFs.getPath(sourceMount.resolve(suffix).toString())
          val target = targetFs.getPath(targetMount.resolve(suffix).toString())
          assertEquals(target, source.resolve(target))
        }
      }
    }
  }

  @TestFactory
  fun `mapped Windows paths on different roots are distinct`() = listOf(
    "@/C/same" to "@/D/same",
    "server/share/same" to "server/other/same",
    "server/share/same" to "other/share/same",
    "@/C/same" to "server/share/same",
  ).map { (first, second) ->
    DynamicTest.dynamicTest("$first != $second") {
      val mount = Path.of("ijent-path-test").toAbsolutePath()
      fileSystem(EelOsFamily.Windows, mount).use { fs ->
        assertNotEquals(fs.getPath(mount.resolve(first).toString()), fs.getPath(mount.resolve(second).toString()))
      }
    }
  }

  @TestFactory
  fun `a path under the root maps to an absolute IJent path`(): List<DynamicTest> {
    val mount = Path.of("ijent-root-test").toAbsolutePath()
    val separator = mount.fileSystem.separator
    val doubledMount = doubleSeparators(mount)
    return listOf(
      mount.toString() to "/",
      "$mount$separator" to "/",
      "$mount${separator}foo${separator}bar" to "/foo/bar",
      "$doubledMount$separator$separator" to "/",
      "$doubledMount${separator}${separator}foo${separator}${separator}bar" to "/foo/bar",
      "$mount/foo\\bar" to "/foo/bar",
    ).map { (localPath, expectedIjentPath) ->
      DynamicTest.dynamicTest(localPath) {
        fileSystem(EelOsFamily.Posix, mount).use { fs ->
          assertEquals(expectedIjentPath, ijentPath(fs.getPath(localPath)))
        }
      }
    }
  }

  @TestFactory
  fun `a path that only starts with the root string is not under the root`(): List<DynamicTest> {
    val mount = Path.of("ijent-root-test").toAbsolutePath()
    val separator = mount.fileSystem.separator
    val doubledMount = doubleSeparators(mount)
    return listOf(
      "$mount-sibling",
      "$mount-sibling${separator}foo",
      "$doubledMount-sibling$separator${separator}foo",
    ).map { localPath ->
      DynamicTest.dynamicTest(localPath) {
        fileSystem(EelOsFamily.Posix, mount).use { fs ->
          // A path outside the root keeps its full string: on Windows, it becomes a relative IJent path,
          // and on Unix, an absolute one. A path that is wrongly taken as under the root loses the root string,
          // and only `-sibling...` stays.
          val ijentPath = ijentPath(fs.getPath(localPath))
          assertFalse(ijentPath.startsWith("-sibling"), "The root string was removed from `$localPath`: `$ijentPath`")
        }
      }
    }
  }

  @EnabledOnOs(OS.WINDOWS)
  @TestFactory
  fun `IJPL-260495 a UNC path with repeated separators is under the root`(): List<DynamicTest> {
    val mount = Path.of("""\\wsl.localhost\Debian\""")
    return listOf(
      """\\wsl.localhost\Debian\usr\bin""" to "/usr/bin",
      """\\wsl.localhost\\Debian\\usr\\bin""" to "/usr/bin",
      """\\\wsl.localhost\\\Debian\\\usr\\\bin""" to "/usr/bin",
      """\\wsl.localhost/Debian/usr/bin""" to "/usr/bin",
      """\\wsl.localhost\\Debian\\""" to "/",
    ).map { (localPath, expectedIjentPath) ->
      DynamicTest.dynamicTest(localPath) {
        fileSystem(EelOsFamily.Posix, mount).use { fs ->
          assertEquals(expectedIjentPath, ijentPath(fs.getPath(localPath)))
        }
      }
    }
  }

  /**
   * Returns [path] with each separator after the root written two times, for example `C:\a\\b` for `C:\a\b`.
   * The root stays as it is: on Unix, a leading `//` is not the same as `/` for the file system under test.
   */
  private fun doubleSeparators(path: Path): String {
    val separator = path.fileSystem.separator
    return path.root.toString() + path.joinToString(separator + separator)
  }

  /**
   * Returns the IJent path that [path] maps to, as a string.
   */
  private fun ijentPath(path: Path): String = (path as IjentEphemeralRootAwarePath).originalPath.toString()

  private fun fileSystem(family: EelOsFamily, mount: Path): FileSystem {
    val descriptor = object : EelDescriptor {
      override val name: String = "NIO path test"
      override val osFamily: EelOsFamily = family
    }
    val scope = object : CoroutineScope {
      override val coroutineContext: CoroutineContext
        get() = error("Path operations must not deploy IJent")
    }
    val api = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = { false })
    val provider = IjentNioFileSystemProvider()
    val uri = URI("ijent://path-test")
    provider.newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(api))
    val mappedProvider: FileSystemProvider = IjentEphemeralRootAwareFileSystemProvider(
      root = mount,
      ijentFsProvider = provider,
      originalFsProvider = FileSystems.getDefault().provider(),
      useRootDirectoriesFromOriginalFs = false,
      eelDescriptor = descriptor,
    )
    return mappedProvider.getFileSystem(uri)
  }
}
