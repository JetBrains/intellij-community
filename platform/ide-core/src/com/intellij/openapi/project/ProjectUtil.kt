// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectUtil")
package com.intellij.openapi.project

import com.intellij.ide.DataManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager.getSystemDir
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.PathUtilRt
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.text.trimMiddle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.*
import javax.swing.JComponent
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString

val NOTIFICATIONS_SILENT_MODE: Key<Boolean> = Key.create("NOTIFICATIONS_SILENT_MODE")

val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

@JvmOverloads @NlsSafe
fun calcRelativeToProjectPath(file: VirtualFile,
                              project: Project?,
                              includeFilePath: Boolean = true,
                              includeUniqueFilePath: Boolean = false,
                              keepModuleAlwaysOnTheLeft: Boolean = false): String {
  if (file is VirtualFilePathWrapper && file.enforcePresentableName()) {
    return if (includeFilePath) file.presentablePath else file.name
  }

  val url = when {
    includeFilePath -> file.presentableUrl
    includeUniqueFilePath && project != null -> UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file)
    else -> file.name
  }

  if (project == null) {
    return url
  }
  else {
    return displayUrlRelativeToProject(file = file,
                                       url = url,
                                       project = project,
                                       isIncludeFilePath = includeFilePath,
                                       moduleOnTheLeft = keepModuleAlwaysOnTheLeft)
  }
}

fun guessProjectForFile(file: VirtualFile): Project? = ProjectLocator.getInstance().guessProjectForFile(file)

/**
 * guessProjectForFile works incorrectly - even if file is config (idea config file), a first opened project will be returned
 */
@JvmOverloads
fun guessProjectForContentFile(file: VirtualFile,
                               fileType: FileType = FileTypeManager.getInstance().getFileTypeByFileName(file.nameSequence)): Project? {
  if (ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType)) {
    return null
  }

  val list = ProjectManager.getInstance().openProjects.filter {
    !it.isDefault && it.isInitialized && !it.isDisposed && ProjectRootManager.getInstance(it).fileIndex.isInContent(file)
  }

  return list.firstOrNull { WindowManager.getInstance().getFrame(it)?.isActive ?: false } ?: list.firstOrNull()
}

fun isProjectOrWorkspaceFile(file: VirtualFile): Boolean = ProjectCoreUtil.isProjectOrWorkspaceFile(file)

@ApiStatus.ScheduledForRemoval
@Deprecated(message = "This method is an unreliable hack, find another way to locate a project instance.")
fun guessCurrentProject(component: JComponent?): Project {
  var project: Project? = null
  if (component != null) {
    project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
  }

  @Suppress("DEPRECATION")
  return project
         ?: ProjectManager.getInstance().openProjects.firstOrNull()
         ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContext)
         ?: ProjectManager.getInstance().defaultProject
}

fun currentOrDefaultProject(project: Project?): Project = project ?: ProjectManager.getInstance().defaultProject

val Project.modules: Array<Module>
  get() = ModuleManager.getInstance(this).modules

inline fun <T> Project.modifyModules(crossinline task: ModifiableModuleModel.() -> T): T {
  val model = ModuleManager.getInstance(this).getModifiableModel()
  val result = model.task()
  ApplicationManager.getApplication().runWriteAction {
    model.commit()
  }
  return result
}

/**
 *  Tries to guess the "main project directory" of the project.
 *
 *  There is no strict definition of what is a project directory, since a project can contain multiple modules located in different places,
 *  and the `.idea` directory can be located elsewhere (making the popular [Project.getBaseDir] method not applicable to get the "project
 *  directory"). This method should be preferred, although it can't provide perfect accuracy either. So its results shouldn't be used for
 *  real actions as is, the user should be able to review and change it. For example, it can be used as a default selection in a file chooser.
 */
fun Project.guessProjectDir() : VirtualFile? {
  if (isDefault) {
    return null
  }

  val baseDirectories = getBaseDirectories()
  val baseDirectory = baseDirectories.firstOrNull { it.fileSystem is LocalFileSystem } ?: baseDirectories.firstOrNull()
  if (baseDirectory != null) {
    return baseDirectory
  }

  val modules = ModuleManager.getInstance(this).modules
  val module = if (modules.size == 1) modules.first() else modules.firstOrNull { it.name == this.name }
  module?.guessModuleDir()?.let { return it }
  return LocalFileSystem.getInstance().findFileByPath(basePath!!)
}

/**
 * Returns some directory which is located near module files.
 * There is no such thing as "base directory" for a module in the IntelliJ project model.
 * A module may have multiple content roots, or not have content roots at all.
 * The module configuration file (.iml) may be located far away from the module files or doesn't exist at all.
 *
 * So this method tries to suggest some directories which are related to the module,
 * but due to its heuristic nature, its result shouldn't be used for real actions as is, user should be able to review and change it.
 * For example, it can be used as a default selection in a file chooser.
 */
fun Module.guessModuleDir(): VirtualFile? {
  val contentRoots = rootManager.contentRoots.filter { it.isDirectory }
  return contentRoots.find { it.name == name } ?: contentRoots.firstOrNull() ?: moduleFile?.parent
}

@JvmOverloads
fun Project.getProjectCacheFileName(isForceNameUse: Boolean = false, hashSeparator: String = ".", extensionWithDot: String = ""): String {
  return getProjectCacheFileName(presentableUrl = presentableUrl,
                                 projectName = name,
                                 isForceNameUse = isForceNameUse,
                                 hashSeparator = hashSeparator,
                                 extensionWithDot = extensionWithDot)
}

/**
 * This is a variant of [getProjectCacheFileName] which can be used in tests before [Project] instance is created
 * @param projectPath value of [Project.getPresentableUrl]
 */
fun getProjectCacheFileName(projectPath: Path): String {
  return getProjectCacheFileName(presentableUrl = projectPath.invariantSeparatorsPathString,
                                 projectName = (projectPath.fileName ?: projectPath).toString(),
                                 isForceNameUse = false,
                                 hashSeparator = ".",
                                 extensionWithDot = "")
}

@Internal
fun getProjectCacheFileName(presentableUrl: String?,
                            projectName: String,
                            isForceNameUse: Boolean = false,
                            hashSeparator: String = ".",
                            extensionWithDot: String = ""): String {
  var name = when {
    isForceNameUse || presentableUrl == null -> projectName
    else -> {
      // the lower case here is used for cosmetic reasons (develar - discussed with jeka - leave it as it was,
      // user projects will not have long names as in our tests
      PathUtilRt.getFileName(presentableUrl).lowercase(Locale.US).removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
    }
  }
  name = sanitizeFileName(name, truncateIfNeeded = false)
  // do not use project.locationHash to avoid prefix for IPR projects (not required in our case because name in any case is prepended)
  val locationHash = Integer.toHexString((presentableUrl ?: name).hashCode())
  // trim name to avoid "File name too long"
  return name.trimMiddle(name.length.coerceAtMost(255 - hashSeparator.length - locationHash.length), useEllipsisSymbol = false) +
         "$hashSeparator$locationHash$extensionWithDot"
}

/**
 * Returns the path to a directory which can be used to store project-specific caches. Note that directory structure used by this 
 * function doesn't allow automatic cleaning of all caches related to a given project if it was deleted, so consider using [getProjectDataPath] 
 * instead.
 */
@JvmOverloads
fun Project.getProjectCachePath(@NonNls cacheDirName: String, isForceNameUse: Boolean = false, extensionWithDot: String = ""): Path {
  return getSystemDir().resolve(cacheDirName).resolve(getProjectCacheFileName(isForceNameUse, extensionWithDot = extensionWithDot))
}

/**
 * Returns a path to a directory which can be used to store project-specific caches.
 * Caches for different projects are stored under different
 * directories, [name] is used to provide different directories for different kinds of caches in the same project.
 *
 * The function is similar to [getProjectCachePath], but all paths returned by this function for the same project
 * are located under the same directory,
 * and if a new project is created with the same name and location as some previously deleted project, it won't reuse its caches.
 */
fun Project.getProjectDataPath(@NonNls name: String): Path {
  return getProjectDataPathRoot(this).resolve(name)
}

/**
 * Root directory for all project-specific caches.
 */
val projectsDataDir: Path
  get() = getSystemDir().resolve("projects")

/**
 * Asynchronously deletes caches directories obtained via [getProjectDataPath] for all projects.
 */
@ApiStatus.Experimental
fun clearCachesForAllProjects(@NonNls dataDirName: String) {
  projectsDataDir.directoryStreamIfExists { dirs ->
    val filesToDelete = dirs.asSequence().map { it.resolve(dataDirName) }.filter { it.exists() }.map { it.toFile() }.toList()
    FileUtil.asyncDelete(filesToDelete)
  }
}

@ApiStatus.Experimental
fun clearCachesForAllProjectsStartingWith(@NonNls prefix: String) {
  require(!prefix.isEmpty())
  // A snapshot list instead of stream is used - do not iterate directory while deleting its content
  for (projectDir in NioFiles.list(projectsDataDir)) {
    for (file in NioFiles.list(projectDir)) {
      if (file.fileName.toString().startsWith(prefix)) {
        NioFiles.deleteRecursively(file)
      }
    }
  }
}

/**
 * Returns the root directory for all caches related to [project].
 */
@Internal
fun getProjectDataPathRoot(project: Project): Path = projectsDataDir.resolve(project.getProjectCacheFileName())

/**
 * Returns the root directory for all caches related to the project at [projectPath].
 */
@Internal
fun getProjectDataPathRoot(projectPath: Path): Path = projectsDataDir.resolve(getProjectCacheFileName(projectPath))

fun Project.getExternalConfigurationDir(): Path {
  return getProjectDataPath("external_build_system")
}

/**
 * Use parameters only for migration purposes; once all usages are migrated, parameters will be removed.
 */
@JvmOverloads
fun Project.getProjectCachePath(baseDir: Path, forceNameUse: Boolean = false, hashSeparator: String = "."): Path {
  return baseDir.resolve(getProjectCacheFileName(forceNameUse, hashSeparator))
}

fun getOpenedProjects(): Sequence<Project> {
  return sequence {
    for (project in ((ProjectManager.getInstanceIfCreated() ?: return@sequence).openProjects)) {
      if (!project.isDisposed && project.isInitialized) {
        yield(project)
      }
    }
  }
}

fun isNotificationSilentMode(project: Project?): Boolean {
  return ApplicationManager.getApplication().isHeadlessEnvironment || NOTIFICATIONS_SILENT_MODE.get(project, false)
}
