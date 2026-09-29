package com.intellij.platform.lsp

import com.intellij.idea.TestFor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files

/**
 * A file on a UNC path, for example on the `\\fileserver\share` network share or in the `\\wsl.localhost\Ubuntu` WSL share, must reach
 * the server as a `file://host/share/...` URI, with the host in the authority.
 */
@TestApplication
@TestFor(issues = ["PY-86270", "PY-88158"])
internal class LspUncFileUriTest {
  companion object {
    private const val UNC_PREFIX = "//wsl.localhost/Ubuntu"

    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture
    private val tempDir by tempDirFixture
  }

  private fun plainDescriptor(): FakeLspClientDescriptor = FakeLspClientDescriptor(project, LspCustomization(), null, null)

  /**
   * A descriptor that reports every file under [UNC_PREFIX], the way the VFS reports a project that is open over a UNC path.
   * The share does not exist, so [FakeLspClientDescriptor.findLocalFileByPath] removes the prefix again.
   * A Windows path such as `C:/dir` gets a `/` separator after the prefix, and a Unix path such as `/dir` keeps its own.
   */
  private fun uncDescriptor(): FakeLspClientDescriptor =
    object : FakeLspClientDescriptor(project, LspCustomization(), null, null) {
      override fun getFilePath(file: VirtualFile): String = UNC_PREFIX + "/" + super.getFilePath(file).trimStart('/')
      override fun findLocalFileByPath(path: String): VirtualFile? = super.findLocalFileByPath(path.removePrefix(UNC_PREFIX))
    }

  /** A descriptor that reports one fixed path, so the URI is the same on every OS. */
  private fun fixedPathDescriptor(path: String): FakeLspClientDescriptor =
    object : FakeLspClientDescriptor(project, LspCustomization(), null, null) {
      override fun getFilePath(file: VirtualFile): String = path
    }

  private fun createFile(name: String): VirtualFile {
    val filePath = tempDir.resolve(name)
    Files.writeString(filePath, "x = 1")
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)) { filePath.toString() }
  }

  @Test
  fun `a unc path puts the host in the authority`() {
    val file = createFile("fixed.py")
    val descriptor = fixedPathDescriptor("//wsl.localhost/Ubuntu/home/me/my project/main.py")
    assertEquals("file://wsl.localhost/Ubuntu/home/me/my%20project/main.py", descriptor.getFileUri(file))
  }

  @Test
  fun `the legacy wsl host puts the host in the authority`() {
    val file = createFile("legacy.py")
    val descriptor = fixedPathDescriptor("//wsl$/Ubuntu/home/me/main.py")
    val uri = descriptor.getFileUri(file)
    assertEquals("file://wsl$/Ubuntu/home/me/main.py", uri)
    assertEquals("wsl$", URI(uri).authority, uri)
  }

  @Test
  fun `a host that cannot be an authority keeps the plain conversion`() {
    val file = createFile("plain-conversion.py")
    val paths = listOf(
      "//host\\share\\main.py",
      "//host@SSL@443/DavWWWRoot/main.py",
      "//host:445/share/main.py",
      "//[::1]/share/main.py",
      "//./pipe/main.py",
      "//?/C:/dir/main.py",
    )
    for (path in paths) {
      val uri = URI(fixedPathDescriptor(path).getFileUri(file))
      assertNull(uri.authority, "$path -> $uri")
      assertEquals(path, uri.path, "$path -> $uri")
    }
  }

  @Test
  fun `a unc file uri round trip`() {
    val file = createFile("main.py")
    val descriptor = uncDescriptor()
    val uri = descriptor.getFileUri(file)
    assertEquals("wsl.localhost", URI(uri).authority, uri)
    assertEquals(file, descriptor.findFileByUri(uri))
  }

  @Test
  fun `a four slash uri still resolves a unc path`() {
    val file = createFile("four-slash.py")
    val descriptor = uncDescriptor()
    // RFC 8089 also allows `file:////host/share/...`, and a server can send it back
    val uri = descriptor.getFileUri(file).replaceFirst("file://", "file:////")
    assertEquals(file, descriptor.findFileByUri(uri))
  }

  @Test
  fun `a plain path keeps the empty authority`() {
    val file = createFile("plain.py")
    val descriptor = plainDescriptor()
    val uri = descriptor.getFileUri(file)
    assertNull(URI(uri).authority, uri)
    assertEquals(file, descriptor.findFileByUri(uri))
  }

  @Test
  fun `a localhost authority resolves a plain path`() {
    val file = createFile("localhost.py")
    val descriptor = plainDescriptor()
    val uri = descriptor.getFileUri(file).replaceFirst("file://", "file://localhost")
    assertEquals(file, descriptor.findFileByUri(uri))
  }

  @Test
  fun `a localhost authority tries the local path before the unc path`() {
    val lookups = mutableListOf<String>()
    val descriptor = object : FakeLspClientDescriptor(project, LspCustomization(), null, null) {
      override fun findLocalFileByPath(path: String): VirtualFile? {
        lookups.add(path)
        return null
      }
    }
    assertNull(descriptor.findFileByUri("file://localhost/share/main.py"))
    assertEquals(listOf("/share/main.py", "//localhost/share/main.py"), lookups)
  }

  @Test
  fun `another authority does not resolve a plain path`() {
    val file = createFile("otherhost.py")
    val descriptor = plainDescriptor()
    // The local path without the host is a file that the server did not name
    val uri = descriptor.getFileUri(file).replaceFirst("file://", "file://fileserver")
    assertNull(descriptor.findFileByUri(uri), uri)
  }
}
