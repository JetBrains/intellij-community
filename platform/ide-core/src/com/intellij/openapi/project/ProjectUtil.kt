// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectUtil")
package com.intellij.openapi.project

import com.intellij.ide.DataManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.PathUtilRt
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.trimMiddle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer
import javax.swing.JComponent

val NOTIFICATIONS_SILENT_MODE = Key.create<Boolean>("NOTIFICATIONS_SILENT_MODE")

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

  return if (project == null) url
         else displayUrlRelativeToProject(file, url, project, includeFilePath, keepModuleAlwaysOnTheLeft)
}

fun guessProjectForFile(file: VirtualFile): Project? = ProjectLocator.getInstance().guessProjectForFile(file)

/**
 * guessProjectForFile works incorrectly - even if file is config (idea config file) first opened project will be returned
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

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
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

inline fun <T> Project.modifyModules(crossinline task: ModifiableModuleModel.() -> T): T {
  val model = ModuleManager.getInstance(this).modifiableModel
  val result = model.task()
  runWriteAction {
    model.commit()
  }
  return result
}

fun isProjectDirectoryExistsUsingIo(parent: VirtualFile): Boolean {
  return try {
    Files.exists(Path.of(parent.path, Project.DIRECTORY_STORE_FOLDER))
  }
  catch (e: InvalidPathException) {
    false
  }
}

private val BASE_DIRECTORY_SUGGESTER_EP_NAME = ExtensionPointName.create<BaseDirectorySuggester>("com.intellij.baseDirectorySuggester")

/**
 *  Tries to guess the "main project directory" of the project.
 *
 *  There is no strict definition of what is a project directory, since a project can contain multiple modules located in different places,
 *  and the `.idea` directory can be located elsewhere (making the popular [Project.getBaseDir] method not applicable to get the "project
 *  directory"). This method should be preferred, although it can't provide perfect accuracy either. So its results shouldn't be used for
 *  real actions as is, user should be able to review and change it. For example, it can be used as a default selection in a file chooser.
 */
fun Project.guessProjectDir() : VirtualFile? {
  if (isDefault) {
    return null
  }
  val customBaseDir = BASE_DIRECTORY_SUGGESTER_EP_NAME.extensions().map { it.suggestBaseDirectory(this) }.filter(Objects::nonNull).findFirst().orElse(null)
  if (customBaseDir != null) {
    return customBaseDir
  }

  val modules = ModuleManager.getInstance(this).modules
  val module = if (modules.size == 1) modules.first() else modules.firstOrNull { it.name == this.name }
  module?.guessModuleDir()?.let { return it }
  return LocalFileSystem.getInstance().findFileByPath(basePath!!)
}

/**
 * Returns some directory which is located near module files.
 *
 * There is no such thing as "base directory" for a module in IntelliJ project model. A module may have multiple content roots, or not have
 * content roots at all. The module configuration file (.iml) may be located far away from the module files or doesn't exist at all. So this
 * method tries to suggest some directory which is related to the module but due to its heuristic nature its result shouldn't be used for
 * real actions as is, user should be able to review and change it. For example, it can be used as a default selection in a file chooser.
 */
fun Module.guessModuleDir(): VirtualFile? {
  val contentRoots = rootManager.contentRoots.filter { it.isDirectory }
  return contentRoots.find { it.name == name } ?: contentRoots.firstOrNull() ?: moduleFile?.parent
}

@JvmOverloads
fun Project.getProjectCacheFileName(isForceNameUse: Boolean = false, hashSeparator: String = ".", extensionWithDot: String = ""): String {
  return getProjectCacheFileName(presentableUrl, name, isForceNameUse, hashSeparator, extensionWithDot)
}

/**
 * This is a variant of [getProjectCacheFileName] which can be used in tests before [Project] instance is created
 * @param projectPath value of [Project.getPresentableUrl]
 */
fun getProjectCacheFileName(projectPath: Path): String {
  return getProjectCacheFileName(projectPath.systemIndependentPath, (projectPath.fileName ?: projectPath).toString(), false, ".", "")
}

private fun getProjectCacheFileName(presentableUrl: String?,
                                    projectName: String,
                                    isForceNameUse: Boolean,
                                    hashSeparator: String,
                                    extensionWithDot: String): String {
  val name = when {
    isForceNameUse || presentableUrl == null -> projectName
    else -> {
      // lower case here is used for cosmetic reasons (develar - discussed with jeka - leave it as it was, user projects will not have long names as in our tests
      PathUtilRt.getFileName(presentableUrl).lowercase(Locale.US).removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
    }
  }
  return doGetProjectFileName(presentableUrl, sanitizeFileName(name, truncateIfNeeded = false), hashSeparator, extensionWithDot)
}

@ApiStatus.Internal
fun doGetProjectFileName(presentableUrl: String?,
                         name: String,
                         hashSeparator: String,
                         extensionWithDot: String): String {
  // do not use project.locationHash to avoid prefix for IPR projects (not required in our case because name in any case is prepended)
  val locationHash = Integer.toHexString((presentableUrl ?: name).hashCode())
  // trim name to avoid "File name too long"
  return "${name.trimMiddle(name.length.coerceAtMost(255 - hashSeparator.length - locationHash.length), useEllipsisSymbol = false)}$hashSeparator$locationHash$extensionWithDot"
}

@JvmOverloads
fun Project.getProjectCachePath(@NonNls cacheDirName: String, isForceNameUse: Boolean = false, extensionWithDot: String = ""): Path {
  return appSystemDir.resolve(cacheDirName).resolve(getProjectCacheFileName(isForceNameUse, extensionWithDot = extensionWithDot))
}

/**
 * Returns path to a directory which can be used to store project-specific caches. Caches for different projects are stored under different
 * directories, [dataDirName] is used to provide different directories for different kinds of caches in the same project.
 *
 * The function is similar to [getProjectCachePath], but all paths returned by this function for the same project are located under the same directory,
 * and if a new project is created with the same name and location as some previously deleted project, it won't reuse its caches.
 */
@ApiStatus.Experimental
fun Project.getProjectDataPath(@NonNls dataDirName: String): Path {
  return getProjectDataPathRoot(this).resolve(dataDirName)
}

val projectsDataDir: Path
  get() = appSystemDir.resolve("projects")

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

@ApiStatus.Internal
fun getProjectDataPathRoot(project: Project): Path = projectsDataDir.resolve(project.getProjectCacheFileName())

@ApiStatus.Internal
fun getProjectDataPathRoot(projectPath: Path): Path = projectsDataDir.resolve(getProjectCacheFileName(projectPath))

fun Project.getExternalConfigurationDir(): Path {
  return getProjectCachePath("external_build_system")
}

/**
 * Use parameters only for migration purposes, once all usages will be migrated, parameters will be removed
 */
@JvmOverloads
fun Project.getProjectCachePath(baseDir: Path, forceNameUse: Boolean = false, hashSeparator: String = "."): Path {
  return baseDir.resolve(getProjectCacheFileName(forceNameUse, hashSeparator))
}

/**
 * Add one-time projectOpened listener.
 */
fun runWhenProjectOpened(project : Project, handler: Runnable) {
  runWhenProjectOpened(project) {
    handler.run()
  }
}

/**
 * Add one-time first projectOpened listener.
 */
@JvmOverloads
fun runWhenProjectOpened(project: Project? = null, handler: Consumer<Project>) {
  runWhenProjectOpened(project) {
    handler.accept(it)
  }
}

/**
 * Add one-time projectOpened listener.
 */
inline fun runWhenProjectOpened(project: Project? = null, crossinline handler: (project: Project) -> Unit) {
  val connection = (project ?: ApplicationManager.getApplication()).messageBus.simpleConnect()
  connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
    override fun projectOpened(eventProject: Project) {
      if (project == null || project === eventProject) {
        connection.disconnect()
        handler(eventProject)
      }
    }
  })
}

inline fun processOpenedProjects(processor: (Project) -> Unit) {
  for (project in (ProjectManager.getInstanceIfCreated()?.openProjects ?: return)) {
    if (project.isDisposed || !project.isInitialized) {
      continue
    }

    processor(project)
  }
}

fun isNotificationSilentMode(project: Project?): Boolean {
  return ApplicationManager.getApplication().isHeadlessEnvironment || NOTIFICATIONS_SILENT_MODE.get(project, false)
}
