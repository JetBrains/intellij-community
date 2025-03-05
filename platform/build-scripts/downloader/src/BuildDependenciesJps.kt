// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.google.common.hash.Funnels
import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.Credentials
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.asText
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getChildElements
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getComponentElement
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getLibraryElement
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getSingleChildElement
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.tryGetSingleChildElement
import org.w3c.dom.Element
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
object BuildDependenciesJps {
  private fun getSystemIndependentPath(path: Path): String = path.toString().replace("\\", "/").removeSuffix("/")

  @JvmStatic
  fun getProjectModule(projectHome: Path, moduleName: String): Path {
    val modulesXml = projectHome.resolve(".idea/modules.xml")
    val root = BuildDependenciesUtil.createDocumentBuilder().parse(modulesXml.toFile()).documentElement
    val moduleManager = root.getComponentElement("ProjectModuleManager")
    val modules = moduleManager.getSingleChildElement("modules")
    val allModules = modules.getChildElements("module")
      .mapNotNull { it.getAttribute("filepath") }
    val moduleFile = allModules.singleOrNull { it.endsWith("/${moduleName}.iml") }
                       ?.replace("\$PROJECT_DIR\$", getSystemIndependentPath(projectHome))
                     ?: error("Unable to find module '$moduleName' in $modulesXml")
    val modulePath = Path.of(moduleFile)
    check(Files.exists(modulePath)) {
      "Module file '$modulePath' does not exist"
    }
    return modulePath
  }

  private fun getLibraryRoots(
    library: Element,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot,
    credentialsProvider: (() -> Credentials)?
  ): List<Path> {
    val properties = library.getSingleChildElement("properties")
    val mavenId = properties.getAttribute("maven-id")

    // every library in Ultimate project must have a sha256 checksum, so all of this data must be present
    // in case of referencing '-SNAPSHOT' versions locally, checksums may be missing
    val verification = properties.tryGetSingleChildElement("verification")
    val artifacts = verification?.getChildElements("artifact")
    val sha256sumMap = artifacts?.associate {
      it.getAttribute("url") to it.getSingleChildElement("sha256sum").textContent.trim()
    } ?: emptyMap()

    val classes = library.getSingleChildElement("CLASSES")
    return classes.getChildElements("root")
      .mapNotNull { it.getAttribute("url") }
      .map {
        it
          .removePrefix("jar:/")
          .replace("\$MAVEN_REPOSITORY\$", "")
          .trim('!', '/')
      }
      .map { relativePath ->
        val fileUrl = "file://\$MAVEN_REPOSITORY\$/${relativePath}"
        val remoteUrl = mavenRepositoryUrl.trimEnd('/') + "/${relativePath}"

        val localMavenFile = getLocalArtifactRepositoryRoot().resolve(relativePath)

        val file = when {
          Files.isRegularFile(localMavenFile) && Files.size(localMavenFile) > 0 -> localMavenFile
          credentialsProvider != null -> BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, URI(remoteUrl), credentialsProvider)
          else -> BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, URI(remoteUrl))
        }

        // '-SNAPSHOT' versions could be used only locally to test new locally built dependencies
        if (!mavenId.endsWith("-SNAPSHOT")) {
          val actualSha256checksum = Files.newInputStream(file).use {
            val hasher = Hashing.sha256().newHasher()
            ByteStreams.copy(it, Funnels.asOutputStream(hasher))
            hasher.hash().toString()
          }

          val expectedSha256Checksum = sha256sumMap[fileUrl] ?: error("SHA256 checksum is missing for $fileUrl:\n${library.asText}")
          if (expectedSha256Checksum != actualSha256checksum) {
            Files.delete(file)
            error("File $file has wrong checksum. On disk: $actualSha256checksum. Expected: $expectedSha256Checksum. Library:\n${library.asText}")
          }
        }
        file
      }
  }

  @JvmStatic
  fun getModuleLibraryRoots(
    iml: Path,
    libraryName: String,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot,
    credentialsProvider: (() -> Credentials)?
  ): List<Path> = try {
    val root = BuildDependenciesUtil.createDocumentBuilder().parse(iml.toFile()).documentElement

    val library = root.getLibraryElement(libraryName, iml)
    val roots = getLibraryRoots(library, mavenRepositoryUrl, communityRoot, credentialsProvider)

    if (roots.isEmpty()) {
      error("No library roots for library '$libraryName' in the following iml file at '$iml':\n${Files.readString(iml)}")
    }

    roots
  }
  catch (t: Throwable) {
    throw IllegalStateException("Unable to find module library '$libraryName' in '$iml'", t)
  }

  @JvmStatic
  fun getModuleLibrarySingleRoot(
    iml: Path,
    libraryName: String,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot
  ) = getModuleLibrarySingleRoot(iml, libraryName, mavenRepositoryUrl, communityRoot, null)

  @JvmStatic
  fun getModuleLibrarySingleRoot(
    iml: Path,
    libraryName: String,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot,
    credentialsProvider: (() -> Credentials)?
  ): Path {

    val roots = getModuleLibraryRoots(iml, libraryName, mavenRepositoryUrl, communityRoot, credentialsProvider)
    if (roots.size != 1) {
      error("Expected one and only one library '$libraryName' root in '$iml', but got ${roots.size}: ${roots.joinToString()}")
    }

    return roots.single()
  }

  fun getProjectLibraryRoots(
    libraryXml: Path,
    libraryName: String,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot,
    credentialsProvider: (() -> Credentials)?
  ): List<Path> = try {
    val document = BuildDependenciesUtil.createDocumentBuilder().parse(libraryXml.toFile())

    val library = document.documentElement.getSingleChildElement("library")
    val roots = getLibraryRoots(library, mavenRepositoryUrl, communityRoot, credentialsProvider)

    if (roots.isEmpty()) {
      error("No library roots for library '$libraryName' in the following iml file at '$libraryXml':\n${Files.readString(libraryXml)}")
    }

    roots
  }
  catch (t: Throwable) {
    throw IllegalStateException("Unable to find module library '$libraryName' in '$libraryXml'", t)
  }

  fun getLocalArtifactRepositoryRoot(): Path {
    val root = System.getProperty("user.home", null) ?: error("'user.home' system property is not found")
    return Path.of(root, ".m2/repository")
  }
}
