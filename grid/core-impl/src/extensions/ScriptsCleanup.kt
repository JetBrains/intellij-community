package com.intellij.database.extensions

import com.intellij.database.GridScopeProvider
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.JBIterable
import com.intellij.util.io.DigestUtil.md5
import com.intellij.util.io.bytesToHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Future
import java.util.regex.Pattern

object ScriptsCleanup {
  private val LOG = Logger.getInstance(ScriptsCleanup::class.java)
  private val BACKUP_FILE_PATTERN: Pattern = Pattern.compile("(.*)\\.old(_\\w+)?")
  private const val LAST_CLEANUP_VERSION = "LAST_EXTRACTOR_SCRIPTS_CLEANUP_VERSION"

  private val mutex = Mutex()

  @JvmStatic
  fun startScriptsCleanup(scriptDir: Path, dirName: String): Future<*> {
    return ApplicationManager.getApplication().service<GridScopeProvider>().cs.launch {
      startScriptsCleanupImpl(scriptDir, dirName)
    }.asCompletableFuture()
  }

  private suspend fun startScriptsCleanupImpl(scriptDir: Path, dirName: String) {
    withContext(Dispatchers.Default + ModalityState.nonModal().asContextElement()) {
      mutex.withLock {
        doCleanupScripts(scriptDir, dirName)
      }
    }
  }

  private suspend fun doCleanupScripts(scriptDir: Path, dirName: String) {
    val key = LAST_CLEANUP_VERSION + "/" + dirName
    val lastCleanupVersion = PropertiesComponent.getInstance().getValue(key)
    val version = ApplicationInfo.getInstance().build.asString()
    if (!ApplicationManager.getApplication().isUnitTestMode && version == lastCleanupVersion) return
    PropertiesComponent.getInstance().setValue(key, version)
    val files = NioFiles.list(scriptDir)
    if (files.isEmpty()) {
      return
    }

    val backupFilesWithNormalizedNames = files
      .mapNotNull { file ->
        val matcher = BACKUP_FILE_PATTERN.matcher(file.fileName.toString())
        if (matcher.matches()) Pair(file, matcher.group(1)) else null
      }
    if (backupFilesWithNormalizedNames.isEmpty()) return

    val nameToHashes = getHashes(dirName)
    if (nameToHashes.isEmpty()) return

    edtWriteAction {
      val filesToDelete = getFilesToDelete(backupFilesWithNormalizedNames, nameToHashes)
        .mapNotNull { file ->
          val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file)
          if (virtualFile == null) {
            LOG.warn("Cannot find virtual file for $file")
          }
          virtualFile
        }
      deleteFiles(filesToDelete)
    }
  }

  private fun getHashes(name: String): Map<String, Set<String?>> {
    val nameToHashes: MutableMap<String, Set<String?>> = HashMap()
    val id = ExtractorScripts.getPluginId()
    val plugin = getPlugin(id)
    if (plugin == null) return emptyMap()
    val pluginClassLoader = plugin.classLoader
    try {
      val resources = pluginClassLoader.getResources("extensions_old/$name")
      if (!resources.hasMoreElements()) {
        LOG.warn("Cannot find $id resource")
        return emptyMap()
      }
      val dirUrl = resources.nextElement()
      val bundledResourcesDir = VfsUtil.findFileByURL(dirUrl)
      if (bundledResourcesDir == null || !bundledResourcesDir.isDirectory) {
        return emptyMap()
      }
      for (child in bundledResourcesDir.children) {
        BufferedReader(InputStreamReader(child.inputStream, StandardCharsets.UTF_8)).use { reader ->
          nameToHashes.put(child.name, JBIterable.from(
            FileUtil.loadLines(reader)).filter { line: String? -> !line!!.isEmpty() }.toSet())
        }
      }
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
    return nameToHashes
  }

  private fun deleteFiles(filesToDelete: List<VirtualFile>) {
    try {
      for (file in filesToDelete) {
        file.delete(ExtensionsService::class.java)
      }
    }
    catch (ignored: IOException) {
    }
  }

  private fun getFilesToDelete(
    backupFilesWithNormalizedNames: List<Pair<Path, String>>,
    nameToHashes: Map<String, Set<String?>>
  ): List<Path> {
    return backupFilesWithNormalizedNames
      .mapNotNull { (path, name) ->
        val hashes = nameToHashes[name]
        if (hashes != null && hashes.contains(getMd5Hex(path))) {
          path
        }
        else {
          null
        }

      }
  }

  @JvmStatic
  fun getMd5Hex(file: Path): String? {
    try {
      val digest = md5()
      digest.update(Files.readString(file).trim { it <= ' ' }.toByteArray(StandardCharsets.UTF_8))
      return bytesToHex(digest.digest())
    }
    catch (ignored: Throwable) {
      return null
    }
  }
}
