// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.ijent.community.impl.ijentFailSafeFileSystemApi
import com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwareFileSystemProvider
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.coroutines.CoroutineContext

class IjentNioPathTest {
  @TestFactory
  fun `absolute paths relativize`() = roots.flatMap { root ->
    listOf(
      Triple("a", "b", "../b"),
      Triple("a/x", "b/y", "../../b/y"),
      Triple("a/x", "a/y", "../y"),
      Triple("a", "a/b", "b"),
      Triple("a/b", "a", ".."),
      Triple("a", "a", ""),
      Triple("", "a", "a"),
      Triple("a", "", ".."),
    ).map { (base, other, expected) ->
      DynamicTest.dynamicTest("$root: $base -> $other") {
        fileSystem(root).use { fs ->
          val basePath = fs.getPath(root + base.replace("/", fs.separator))
          val otherPath = fs.getPath(root + other.replace("/", fs.separator))
          val relative = basePath.relativize(otherPath)

          assertEquals(expected.replace("/", fs.separator), relative.toString())
          assertEquals(fs.getPath(expected.replace("/", fs.separator)), relative)
          assertEquals(otherPath, basePath.resolve(relative).normalize())
        }
      }
    }
  }

  @TestFactory
  fun `roots have no parent or file name`() = roots.map { root ->
    DynamicTest.dynamicTest(root) {
      fileSystem(root).use { fs ->
        val path = fs.getPath(root)
        assertEquals(path, path.root)
        assertEquals(0, path.nameCount)
        assertNull(path.parent)
        assertNull(path.fileName)
      }
    }
  }

  @TestFactory
  fun `mapped Windows paths preserve their URI`() = listOf("@/C/project", "server/share/project").map { suffix ->
    DynamicTest.dynamicTest(suffix) {
      val mount = Path.of("ijent-path-test").toAbsolutePath()
      fileSystem("C:\\", mount).use { fs ->
        val localPath = mount.resolve(suffix)
        assertEquals(localPath.toUri(), fs.getPath(localPath.toString()).toUri())
      }
    }
  }

  @TestFactory
  fun `absolute paths keep their environment when resolved`() = roots.map { root ->
    DynamicTest.dynamicTest(root) {
      val sourceMount = Path.of("ijent-source-test").toAbsolutePath()
      val targetMount = Path.of("ijent-target-test").toAbsolutePath()
      val suffix = when (root) {
        "/" -> "project"
        "C:\\" -> "@/C/project"
        else -> "server/share/project"
      }
      fileSystem(root, sourceMount).use { sourceFs ->
        fileSystem(root, targetMount).use { targetFs ->
          val source = sourceFs.getPath(sourceMount.resolve(suffix).toString())
          val target = targetFs.getPath(targetMount.resolve(suffix).toString())
          assertEquals(target, source.resolve(target))
        }
      }
    }
  }

  @TestFactory
  fun `Windows path comparisons ignore case`() = roots.filter { it != "/" }.map { root ->
    DynamicTest.dynamicTest(root) {
      fileSystem(root).use { fs ->
        val path = fs.getPath(root + "Projects\\demo")
        val samePath = fs.getPath(root.lowercase() + "PROJECTS\\DEMO")
        assertEquals(path, samePath)
        assertEquals(path.hashCode(), samePath.hashCode())
        assertEquals("..\\other", path.relativize(fs.getPath(root + "projects\\other")).toString())
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
      fileSystem("C:\\", mount).use { fs ->
        assertNotEquals(fs.getPath(mount.resolve(first).toString()), fs.getPath(mount.resolve(second).toString()))
      }
    }
  }

  private fun fileSystem(root: String, mount: Path? = null): FileSystem {
    val descriptor = object : EelDescriptor {
      override val name: String = "NIO path test"
      override val osFamily: EelOsFamily = if (root == "/") EelOsFamily.Posix else EelOsFamily.Windows
    }
    val scope = object : CoroutineScope {
      override val coroutineContext: CoroutineContext
        get() = error("Path operations must not deploy IJent")
    }
    val api = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = { false })
    val provider = IjentNioFileSystemProvider()
    val uri = URI("ijent://path-test")
    val fs = provider.newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(api))
    if (mount == null) return fs
    val mappedProvider: FileSystemProvider = IjentEphemeralRootAwareFileSystemProvider(
      root = mount,
      ijentFsProvider = provider,
      originalFsProvider = FileSystems.getDefault().provider(),
      useRootDirectoriesFromOriginalFs = false,
      eelDescriptor = descriptor,
    )
    return mappedProvider.getFileSystem(uri)
  }

  companion object {
    private val roots = listOf("/", "C:\\", "\\\\server\\share\\")
  }
}
