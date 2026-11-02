// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies

import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfigurationService
import com.sun.net.httpserver.HttpServer
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestBase
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.ArtifactVerification
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsChecksumUtil
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DependencyResolvingBuilderTest : JpsBuildTestBase() {
  private lateinit var mavenCacheArtifact: Path
  private lateinit var remoteArtifact: Path
  private lateinit var expectedChecksum: String

  @BeforeEach
  fun configureRepositories() {
    val localRepository = createTempDirectory("maven-cache").toPath()
    val remoteRepository = createTempDirectory("upstream-repository").toPath()
    mavenCacheArtifact = localRepository.resolve("test/single/1/single-1.jar")
    remoteArtifact = remoteRepository.resolve("test/single/1/single-1.jar")
    writeArtifact(remoteArtifact, "valid artifact")
    expectedChecksum = JpsChecksumUtil.getSha256Checksum(remoteArtifact)
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(myModel.global)
      .addPathVariable("MAVEN_REPOSITORY", localRepository.toString())
    JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(myProject).repositories =
      listOf(JpsRemoteRepositoryDescription("test", "Test repository", remoteRepository.toUri().toString()))
    JpsDependencyResolverConfigurationService.getInstance().getOrCreateDependencyResolverConfiguration(myProject).apply {
      isSha256ChecksumVerificationEnabled = true
      isBindRepositoryEnabled = true
    }
  }

  @Test
  fun `corrupt cached artifact is downloaded again`() {
    writeArtifact(mavenCacheArtifact, "corrupt artifact")
    val actualChecksum = JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact)
    addLibrary()

    val result = resolveLibraries()

    result.assertSuccessful()
    assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    val warning = result.getMessages(BuildMessage.Kind.WARNING).single().messageText
    assertTrue(warning.contains(expectedChecksum))
    assertTrue(warning.contains(actualChecksum))
    assertTrue(warning.contains("Downloading the artifact again"))
  }

  @Test
  fun `corrupt downloaded artifact fails after three retries`() {
    writeArtifact(remoteArtifact, "corrupt replacement")
    val actualChecksum = JpsChecksumUtil.getSha256Checksum(remoteArtifact)
    addLibrary()

    val corruptArtifact = Files.readAllBytes(remoteArtifact)
    withArtifactServer({ corruptArtifact }) { downloads ->
      val result = resolveLibraries()

      result.assertFailed()
      assertEquals(4, downloads.get())
      val warnings = result.getMessages(BuildMessage.Kind.WARNING)
      assertEquals(3, warnings.size)
      warnings.forEachIndexed { index, warning ->
        assertTrue(warning.messageText.contains("attempt ${index + 1} of 3"))
      }
      val errors = result.getMessages(BuildMessage.Kind.ERROR).joinToString { it.messageText }
      assertTrue(errors.contains(expectedChecksum))
      assertTrue(errors.contains(actualChecksum))
      assertEquals(actualChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    }
  }

  @Test
  fun `third download can recover the artifact`() {
    writeArtifact(mavenCacheArtifact, "corrupt cached artifact")
    val corruptArtifact = Files.readAllBytes(mavenCacheArtifact)
    val validArtifact = Files.readAllBytes(remoteArtifact)
    addLibrary()

    withArtifactServer({ attempt -> if (attempt < 3) corruptArtifact else validArtifact }) { downloads ->
      val result = resolveLibraries()

      result.assertSuccessful()
      assertEquals(3, downloads.get())
      assertEquals(3, result.getMessages(BuildMessage.Kind.WARNING).size)
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    }
  }

  @Test
  fun `a valid project JAR leaves a corrupt Maven cache untouched`() {
    val projectArtifact = createTempDirectory("project-lib").toPath().resolve(mavenCacheArtifact.fileName)
    Files.copy(remoteArtifact, projectArtifact)
    writeArtifact(mavenCacheArtifact, "corrupt cached artifact")
    val cachedChecksum = JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact)
    val validArtifact = Files.readAllBytes(remoteArtifact)
    addLibrary(projectArtifact)

    withArtifactServer({ validArtifact }) { downloads ->
      val result = resolveLibraries()

      result.assertSuccessful()
      assertEquals(0, downloads.get())
      assertTrue(result.getMessages(BuildMessage.Kind.WARNING).isEmpty())
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(projectArtifact))
      assertEquals(cachedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    }
  }

  @Test
  fun `a corrupt project JAR is restored from the Maven cache without a download`() {
    val projectArtifact = createTempDirectory("project-lib").toPath().resolve(mavenCacheArtifact.fileName)
    writeArtifact(projectArtifact, "corrupt project artifact")
    Files.createDirectories(mavenCacheArtifact.parent)
    Files.copy(remoteArtifact, mavenCacheArtifact)
    val validArtifact = Files.readAllBytes(remoteArtifact)
    addLibrary(projectArtifact)

    withArtifactServer({ validArtifact }) { downloads ->
      val result = resolveLibraries()

      result.assertSuccessful()
      assertEquals(0, downloads.get())
      assertEquals(1, result.getMessages(BuildMessage.Kind.WARNING).size)
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(projectArtifact))
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    }
  }

  @Test
  fun `a corrupt project JAR is downloaded when the Maven cache is empty`() {
    val projectArtifact = createTempDirectory("project-lib").toPath().resolve(mavenCacheArtifact.fileName)
    writeArtifact(projectArtifact, "corrupt project artifact")
    assertTrue(Files.notExists(mavenCacheArtifact))
    val validArtifact = Files.readAllBytes(remoteArtifact)
    addLibrary(projectArtifact)

    withArtifactServer({ validArtifact }) { downloads ->
      val result = resolveLibraries()

      result.assertSuccessful()
      assertEquals(1, downloads.get())
      assertEquals(1, result.getMessages(BuildMessage.Kind.WARNING).size)
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(projectArtifact))
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    }
  }

  @Test
  fun `a corrupt project JAR is downloaded when the Maven cache is corrupt`() {
    val projectArtifact = createTempDirectory("project-lib").toPath().resolve(mavenCacheArtifact.fileName)
    writeArtifact(projectArtifact, "corrupt project artifact")
    writeArtifact(mavenCacheArtifact, "corrupt cached artifact")
    val validArtifact = Files.readAllBytes(remoteArtifact)
    addLibrary(projectArtifact)

    withArtifactServer({ validArtifact }) { downloads ->
      val result = resolveLibraries()

      result.assertSuccessful()
      assertEquals(1, downloads.get())
      assertEquals(1, result.getMessages(BuildMessage.Kind.WARNING).size)
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(projectArtifact))
      assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
    }
  }

  @Test
  fun `valid cached artifact needs no download`() {
    Files.createDirectories(mavenCacheArtifact.parent)
    Files.copy(remoteArtifact, mavenCacheArtifact)
    Files.delete(remoteArtifact)
    addLibrary()

    val result = resolveLibraries()

    result.assertSuccessful()
    assertTrue(result.getMessages(BuildMessage.Kind.WARNING).isEmpty())
    assertEquals(expectedChecksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
  }

  @Test
  fun `missing verification metadata does not replace the artifact`() {
    writeArtifact(mavenCacheArtifact, "unverified artifact")
    val checksum = JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact)
    addLibrary(includeVerification = false)

    val result = resolveLibraries()

    result.assertFailed()
    assertTrue(result.getMessages(BuildMessage.Kind.WARNING).isEmpty())
    assertEquals(checksum, JpsChecksumUtil.getSha256Checksum(mavenCacheArtifact))
  }

  private fun addLibrary(libraryRoot: Path = mavenCacheArtifact, includeVerification: Boolean = true) {
    val url = JpsPathUtil.pathToUrl(libraryRoot.toString())
    val verification = if (includeVerification) listOf(ArtifactVerification(url, expectedChecksum)) else emptyList()
    val descriptor = JpsMavenRepositoryLibraryDescriptor("test:single:1", false, emptyList(), verification, "test")
    val library = myProject.libraryCollection.addLibrary(
      "test", JpsRepositoryLibraryType.INSTANCE, JpsElementFactory.getInstance().createSimpleElement(descriptor)
    )
    library.addRoot(url, JpsOrderRootType.COMPILED)
  }

  private fun resolveLibraries() = doBuild(
    CompileScopeTestBuilder.rebuild().targetTypes(ProjectDependenciesResolver.ProjectDependenciesResolvingTargetType.INSTANCE)
  )

  private fun withArtifactServer(content: (Int) -> ByteArray, test: (AtomicInteger) -> Unit) {
    val downloads = AtomicInteger()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
      try {
        if (exchange.requestURI.path != "/test/single/1/single-1.jar") {
          exchange.sendResponseHeaders(404, -1)
        }
        else if (exchange.requestMethod == "HEAD") {
          exchange.sendResponseHeaders(200, -1)
        }
        else {
          val bytes = content(downloads.incrementAndGet())
          exchange.sendResponseHeaders(200, bytes.size.toLong())
          exchange.responseBody.use { it.write(bytes) }
        }
      }
      finally {
        exchange.close()
      }
    }
    server.start()
    try {
      JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(myProject).repositories =
        listOf(JpsRemoteRepositoryDescription("test", "Test repository", "http://127.0.0.1:${server.address.port}/"))
      test(downloads)
    }
    finally {
      server.stop(0)
    }
  }

  private fun writeArtifact(path: Path, content: String) {
    Files.createDirectories(path.parent)
    ZipOutputStream(Files.newOutputStream(path)).use { zip ->
      zip.putNextEntry(ZipEntry("artifact.txt").apply { time = 0 })
      zip.write(content.toByteArray())
      zip.closeEntry()
    }
  }
}
