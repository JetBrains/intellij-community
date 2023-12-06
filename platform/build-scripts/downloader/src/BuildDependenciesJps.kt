// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
object BuildDependenciesJps {
  private fun getSystemIndependentPath(path: Path): String = path.toString().replace("\\", "/").removeSuffix("/")

  @JvmStatic
  fun getProjectModule(projectHome: Path, moduleName: String): Path {
    val modulesXml = projectHome.resolve(".idea/modules.xml")
    val root = BuildDependenciesUtil.createDocumentBuilder().parse(modulesXml.toFile()).documentElement
    val moduleManager = BuildDependenciesUtil.getComponentElement(root, "ProjectModuleManager")
    val modules = BuildDependenciesUtil.getSingleChildElement(moduleManager, "modules")
    val allModules = BuildDependenciesUtil.getChildElements(modules, "module")
      .mapNotNull { it.getAttribute("filepath") }
    val moduleFile = allModules.singleOrNull { it.endsWith("/${moduleName}.iml") }
                       ?.replace("\$PROJECT_DIR\$", getSystemIndependentPath(projectHome))
                     ?: error("Unable to find module '$moduleName' in $modulesXml")
    val modulePath = Path.of(moduleFile)
    check(modulePath.exists()) {
      "Module file '$modulePath' does not exist"
    }
    return modulePath
  }

  @JvmStatic
  fun getModuleLibraryRoots(
    iml: Path,
    libraryName: String,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot,
    username: String?,
    password: String?
  ): List<Path> = try {
    val root = BuildDependenciesUtil.createDocumentBuilder().parse(iml.toFile()).documentElement

    val library = BuildDependenciesUtil.getLibraryElement(root, libraryName, iml)
    val classes = BuildDependenciesUtil.getSingleChildElement(library, "CLASSES")
    val roots = BuildDependenciesUtil.getChildElements(classes, "root")
      .mapNotNull { it.getAttribute("url") }
      .map { it
        .removePrefix("jar:/")
        .trim('!', '/')
        .replace("\$MAVEN_REPOSITORY\$", mavenRepositoryUrl.trimEnd('/')) }
      .map {
        if (username != null && password != null)
          BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, URI(it), username, password)
        else
          BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, URI(it))
      }

    if (roots.isEmpty()) {
      error("No library roots for library '$libraryName'")
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
  ) = getModuleLibrarySingleRoot(iml, libraryName, mavenRepositoryUrl, communityRoot, null, null)

  @JvmStatic
  fun getModuleLibrarySingleRoot(
    iml: Path,
    libraryName: String,
    mavenRepositoryUrl: String,
    communityRoot: BuildDependenciesCommunityRoot,
    username: String?,
    password: String?
  ): Path {

    val roots = getModuleLibraryRoots(iml, libraryName, mavenRepositoryUrl, communityRoot, username, password)
    if (roots.size != 1) {
      error("Expected one and only one library '$libraryName' root in '$iml', but got ${roots.size}: ${roots.joinToString()}")
    }

    return roots.single()
  }
}
