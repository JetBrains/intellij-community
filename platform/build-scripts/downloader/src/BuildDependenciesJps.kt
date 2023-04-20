// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

@ApiStatus.Internal
object BuildDependenciesJps {
  private fun getSystemIndependentPath(path: Path): String = path.toString().replace("\\", "/").removeSuffix("/")

  private fun getMavenRepositoryMacro(): String {
    val m2 = Paths.get(System.getProperty("user.home"), ".m2", "repository")
    return getSystemIndependentPath(m2)
  }

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
  fun getModuleLibraryRoots(iml: Path, libraryName: String): List<Path> = try {
    val root = BuildDependenciesUtil.createDocumentBuilder().parse(iml.toFile()).documentElement
    val rootManager = BuildDependenciesUtil.getComponentElement(root, "NewModuleRootManager")
    val library = BuildDependenciesUtil.getChildElements(rootManager, "orderEntry")
                    .filter { it.getAttribute("type") == "module-library" }
                    .map { BuildDependenciesUtil.getSingleChildElement(it, "library") }
                    .singleOrNull { it.getAttribute("name") == libraryName }
                  ?: error("Library '$libraryName' was not found in '$iml'")
    val classes = BuildDependenciesUtil.getSingleChildElement(library, "CLASSES")
    val roots = BuildDependenciesUtil.getChildElements(classes, "root")
      .mapNotNull { it.getAttribute("url") }
      .map { it
        .removePrefix("jar:/")
        .trim('!', '/')
        .replace("\$MAVEN_REPOSITORY\$", getMavenRepositoryMacro()) }
      .map { Path.of(it) }

    if (roots.isEmpty()) {
      error("No library roots for library '$libraryName'")
    }

    for (rootPath in roots) {
      if (!rootPath.exists()) {
        error("Library root from '$iml' was not found on disk: ${rootPath}\n" +
              "IDEA downloads all maven library on opening the project. Probably reopening the project will help")
      }
    }

    roots
  }
  catch (t: Throwable) {
    throw IllegalStateException("Unable to find module library '$libraryName' in '$iml'", t)
  }

  @JvmStatic
  fun getModuleLibrarySingleRoot(iml: Path, libraryName: String): Path {
    val roots = getModuleLibraryRoots(iml, libraryName)
    if (roots.size != 1) {
      error("Expected one and only one library '$libraryName' root in '$iml', but got ${roots.size}: ${roots.joinToString()}")
    }
    return roots.single()
  }
}
