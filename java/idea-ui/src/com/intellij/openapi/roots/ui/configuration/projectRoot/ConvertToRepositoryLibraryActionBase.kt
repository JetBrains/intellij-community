// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryAttachDialog
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorBase
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.io.File
import java.io.IOException
import java.util.*

private val LOG = logger<ConvertToRepositoryLibraryActionBase>()

abstract class ConvertToRepositoryLibraryActionBase(protected val context: StructureConfigurableContext) :
  DumbAwareAction(JavaUiBundle.messagePointer("action.text.convert.to.repository.library"),
                  JavaUiBundle.messagePointer("action.description.convert.to.repository.library"), null) {

  protected val project: Project = context.project

  protected abstract fun getSelectedLibrary(): LibraryEx?

  override fun update(e: AnActionEvent) {
    val library = getSelectedLibrary()
    e.presentation.isEnabledAndVisible = library != null && library.kind == null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val library = getSelectedLibrary() ?: return
    val mavenCoordinates = detectOrSpecifyMavenCoordinates(library) ?: return

    downloadLibraryAndReplace(library, mavenCoordinates)
  }

  private fun downloadLibraryAndReplace(library: LibraryEx,
                                        mavenCoordinates: JpsMavenRepositoryLibraryDescriptor) {
    val libraryProperties = RepositoryLibraryProperties(mavenCoordinates.groupId, mavenCoordinates.artifactId, mavenCoordinates.version,
                                                        mavenCoordinates.isIncludeTransitiveDependencies, mavenCoordinates.excludedDependencies)
    val hasSources = RepositoryUtils.libraryHasSources(library)
    val hasJavadoc = RepositoryUtils.libraryHasJavaDocs(library)
    LOG.debug("Resolving $mavenCoordinates")
    val roots: Collection<OrderRoot> =
      JarRepositoryManager.loadDependenciesModal(project, libraryProperties, hasSources, hasJavadoc, null, null)

    val downloadedFiles = roots.filter { it.type == OrderRootType.CLASSES }.map { VfsUtilCore.virtualToIoFile(it.file) }
    if (downloadedFiles.isEmpty()) {
      if (Messages.showYesNoDialog(JavaUiBundle.message("dialog.message.no.files.were.downloaded"),
                                   JavaUiBundle.message("dialog.title.no.files.were.downloaded"),
                                   null) != Messages.YES) {
        return
      }
      changeCoordinatesAndRetry(mavenCoordinates, library)
      return
    }

    val libraryFiles = library.getFiles(OrderRootType.CLASSES).map { VfsUtilCore.virtualToIoFile(it) }
    val task = ComparingJarFilesTask(project, downloadedFiles, libraryFiles)
    task.queue()
    if (task.cancelled) return

    if (!task.filesAreTheSame) {
      val dialog = LibraryJarsDiffDialog(task.libraryFileToCompare, task.downloadedFileToCompare, mavenCoordinates,
                                         library.presentableName, project)
      dialog.show()
      task.deleteTemporaryFiles()
      when (dialog.exitCode) {
        DialogWrapper.CANCEL_EXIT_CODE -> return
        LibraryJarsDiffDialog.CHANGE_COORDINATES_CODE -> {
          changeCoordinatesAndRetry(mavenCoordinates, library)
          return
        }
      }
    }
    ApplicationManager.getApplication().invokeLater {
      replaceByLibrary(library,
                       object : NewLibraryConfiguration(library.name ?: "", RepositoryLibraryType.getInstance(), libraryProperties) {
                         override fun addRoots(editor: LibraryEditor) {
                           editor.addRoots(roots)
                         }
                       })
    }
  }

  private fun changeCoordinatesAndRetry(mavenCoordinates: JpsMavenRepositoryLibraryDescriptor, library: LibraryEx) {
    val coordinates = specifyMavenCoordinates(listOf(mavenCoordinates)) ?: return
    ApplicationManager.getApplication().invokeLater {
      downloadLibraryAndReplace(library, coordinates)
    }
  }

  private fun detectOrSpecifyMavenCoordinates(library: Library): JpsMavenRepositoryLibraryDescriptor? {
    val detectedCoordinates = detectMavenCoordinates(library.getFiles(OrderRootType.CLASSES))
    LOG.debug("Maven coordinates for ${library.presentableName} JARs: $detectedCoordinates")
    if (detectedCoordinates.size == 1) {
      return detectedCoordinates[0]
    }
    val message = if (detectedCoordinates.isEmpty()) JavaUiBundle.message("dialog.message.cannot.detect.maven.coordinates")
    else JavaUiBundle.message("dialog.message.multiple.maven.coordinates")

    if (Messages.showYesNoDialog(project, "$message. ${JavaUiBundle.message("dialog.message.do.you.want")}",
                                 JavaUiBundle.message("dialog.title.cannot.detect.maven.coordinates"), null) != Messages.YES) {
      return null
    }
    return specifyMavenCoordinates(detectedCoordinates)
  }

  private fun specifyMavenCoordinates(detectedCoordinates: List<JpsMavenRepositoryLibraryDescriptor>): JpsMavenRepositoryLibraryDescriptor? {
    val dialog = RepositoryAttachDialog(project, detectedCoordinates.firstOrNull()?.mavenId, RepositoryAttachDialog.Mode.SEARCH)
    if (!dialog.showAndGet()) {
      return null
    }

    return dialog.selectedLibraryDescriptor
  }

  private fun replaceByLibrary(library: Library, configuration: NewLibraryConfiguration) {
    val annotationUrls = library.getUrls(AnnotationOrderRootType.getInstance())
    val libraryRoots = library.getFiles(OrderRootType.CLASSES) + library.getFiles(OrderRootType.SOURCES)
    context.modulesConfigurator.projectStructureConfigurable.registerObsoleteLibraryRoots(libraryRoots.asList())
    replaceLibrary(library) { editor ->
      editor.properties = configuration.properties
      editor.removeAllRoots()
      configuration.addRoots(editor)
      annotationUrls.forEach { editor.addRoot(it, AnnotationOrderRootType.getInstance()) }
    }
  }

  protected abstract fun replaceLibrary(library: Library, configureNewLibrary: (LibraryEditorBase) -> Unit)

  companion object {
    fun detectMavenCoordinates(libraryRoots: Array<out VirtualFile>): List<JpsMavenRepositoryLibraryDescriptor> =
      libraryRoots.flatMap { root ->
        val pomPropertiesFiles = root.findFileByRelativePath("META-INF/maven")?.children?.flatMap { groupDir ->
          groupDir.children?.mapNotNull { artifactDir ->
            artifactDir.findChild("pom.properties")
          } ?: emptyList()
        } ?: emptyList()
        pomPropertiesFiles.mapNotNull { parsePomProperties(it) }
      }

    private fun parsePomProperties(virtualFile: VirtualFile): JpsMavenRepositoryLibraryDescriptor? {
      val properties = Properties()
      try {
        virtualFile.inputStream.use { properties.load(it) }
      }
      catch(e: IOException) {
        return null
      }
      val groupId = properties.getProperty("groupId")
      val artifactId = properties.getProperty("artifactId")
      val version = properties.getProperty("version")
      return if (groupId != null && artifactId != null && version != null) JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version) else null
    }
  }
}

private class ComparingJarFilesTask(project: Project, private val downloadedFiles: List<File>,
                                    private val libraryFiles: List<File>) : Task.Modal(project, JavaUiBundle.message("task.title.comparing.jar.files"), true) {
  var cancelled = false
  var filesAreTheSame = false
  lateinit var downloadedFileToCompare: VirtualFile
  lateinit var libraryFileToCompare: VirtualFile
  val filesToDelete = ArrayList<File>()

  override fun run(indicator: ProgressIndicator) {
    filesAreTheSame = filesAreTheSame()

    if (!filesAreTheSame) {
      val libraryIoFileToCompare: File
      val downloadedIoFileToCompare: File
      if (libraryFiles.size == 1 && downloadedFiles.size == 1) {
        libraryIoFileToCompare = libraryFiles[0]
        //ensure that the files have the same name so DirDiffViewer will show differences between them properly
        if (downloadedFiles[0].name == libraryFiles[0].name) {
          downloadedIoFileToCompare = downloadedFiles[0]
        }
        else {
          val downloadedFilesIoDir = FileUtil.createTempDirectory("downloaded_file", "", true)
          downloadedIoFileToCompare = File(downloadedFilesIoDir, libraryFiles[0].name)
          FileUtil.copy(downloadedFiles[0], downloadedIoFileToCompare)
          filesToDelete += downloadedFilesIoDir
        }
      }
      else {
        //probably we can enhance DirDiffViewer to accept set of files to avoid copying files into temporary directory
        libraryIoFileToCompare = FileUtil.createTempDirectory("library_files", "", true)
        filesToDelete += libraryIoFileToCompare
        libraryFiles.forEach { FileUtil.copy(it, File(libraryIoFileToCompare, it.name)) }

        downloadedIoFileToCompare = FileUtil.createTempDirectory("downloaded_files", "", true)
        downloadedFiles.forEach { FileUtil.copy(it, File(downloadedIoFileToCompare, it.name)) }
        filesToDelete += downloadedIoFileToCompare
      }

      WriteAction.computeAndWait(ThrowableComputable<Unit, RuntimeException> {
        libraryFileToCompare = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libraryIoFileToCompare)!!
        downloadedFileToCompare = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(downloadedIoFileToCompare)!!
      })

      RefreshQueue.getInstance().refresh(false, false, null, libraryFileToCompare, downloadedFileToCompare)

      val jarFilesToRefresh = ArrayList<VirtualFile>()
      WriteAction.computeAndWait(ThrowableComputable<Unit, RuntimeException> {
        collectNestedJars(libraryFileToCompare, jarFilesToRefresh)
        collectNestedJars(downloadedFileToCompare, jarFilesToRefresh)
      })

      RefreshQueue.getInstance().refresh(false, true, null, jarFilesToRefresh)
    }
  }

  private fun collectNestedJars(file: VirtualFile, result: MutableList<VirtualFile>) {
    if (file.isDirectory) {
      file.children.forEach { collectNestedJars(it, result) }
    }
    else if (FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE)) {
      val jarRootUrl = VfsUtil.getUrlForLibraryRoot(VfsUtil.virtualToIoFile(file))
      VirtualFileManager.getInstance().refreshAndFindFileByUrl(jarRootUrl)?.let { result.add(it) }
    }
  }

  fun deleteTemporaryFiles() {
    FileUtil.asyncDelete(filesToDelete)
  }

  override fun onCancel() {
    cancelled = true
  }

  private fun filesAreTheSame(): Boolean {
    LOG.debug {"Downloaded files: ${downloadedFiles.joinToString { "${it.name} (${it.length()} bytes)" }}"}
    LOG.debug {"Library files: ${libraryFiles.joinToString { "${it.name} (${it.length()} bytes)" }}"}
    if (downloadedFiles.size != libraryFiles.size) {
      return false
    }
    val contentHashing = object : Hash.Strategy<File> {
      override fun hashCode(file: File?) = file?.length()?.toInt() ?: 0

      override fun equals(o1: File?, o2: File?): Boolean {
        if (o1 === o2) {
          return true
        }
        if (o1 == null || o2 == null) {
          return true
        }

        val equal = contentEqual(o1, o2)
        LOG.debug { " comparing files: ${o1.absolutePath}${if (equal) "==" else "!="}${o2.absolutePath}" }
        return equal
      }
    }
    return ObjectOpenCustomHashSet(downloadedFiles, contentHashing) == ObjectOpenCustomHashSet(libraryFiles, contentHashing)
  }

  private fun contentEqual(file1: File, file2: File): Boolean {
    if (file1.length() != file2.length()) return false

    file1.inputStream().use { input1 ->
      file2.inputStream().use { input2 ->
        val buffer1 = ByteArray(4096)
        val buffer2 = ByteArray(4096)
        while (true) {
          val len1 = input1.read(buffer1)
          val len2 = input2.read(buffer2)
          if (len1 != len2) return false
          if (len1 <= 0) break
          for (i in 0 until len1) {
            if (buffer1[i] != buffer2[i]) return false
          }
        }
        return true
      }
    }
  }
}
