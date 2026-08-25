package com.intellij.platform.lsp

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.common.FakeLspServerDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@TestApplication
internal class LspJarFileUriTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val tempDir by tempDirFixture
  }

  @Test
  fun `jar file uri round trip`() = timeoutRunBlocking {
    val jarPath = tempDir.resolve("lib.jar")
    ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
      zip.putNextEntry(ZipEntry("com/foo/Bar.txt"))
      zip.write("class Bar".toByteArray())
      zip.closeEntry()
    }
    val jarVfsPath = jarPath.toString().replace('\\', '/') + "!/com/foo/Bar.txt"
    val jarEntry = JarFileSystem.getInstance().refreshAndFindFileByPath(jarVfsPath)
    assertNotNull(jarEntry, jarVfsPath)

    val descriptor = FakeLspServerDescriptor(project, LspCustomization(), null, null)
    val uri = descriptor.getFileUri(jarEntry!!)
    assertTrue(uri.startsWith("jar:///"), uri)
    assertTrue(uri.endsWith("!/com/foo/Bar.txt"), uri)

    assertEquals(jarEntry, descriptor.findFileByUri(uri))
  }

  @Test
  fun `jar file uri round trip maps the jar path`() = timeoutRunBlocking {
    val jarPath = tempDir.resolve("mapped.jar")
    ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
      zip.putNextEntry(ZipEntry("com/foo/Bar.txt"))
      zip.write("class Bar".toByteArray())
      zip.closeEntry()
    }
    // the jar is created behind the VFS's back: bring the local file in first
    val localJarPath = jarPath.toString().replace('\\', '/')
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath(localJarPath), localJarPath)
    val jarVfsPath = "$localJarPath!/com/foo/Bar.txt"
    val jarEntry = JarFileSystem.getInstance().refreshAndFindFileByPath(jarVfsPath)
    assertNotNull(jarEntry, jarVfsPath)

    // a descriptor with a path mapping, the way an Eel-backed project maps host paths for a WSL or Docker server
    val descriptor = object : FakeLspServerDescriptor(project, LspCustomization(), null, null) {
      override fun getFilePath(file: VirtualFile): String = "/remote" + super.getFilePath(file)
      override fun findLocalFileByPath(path: String): VirtualFile? = super.findLocalFileByPath(path.removePrefix("/remote"))
    }
    val uri = descriptor.getFileUri(jarEntry!!)
    assertTrue(uri.startsWith("jar:///remote"), uri)
    assertTrue(uri.endsWith("!/com/foo/Bar.txt"), uri)

    assertEquals(jarEntry, descriptor.findFileByUri(uri))
  }
}
