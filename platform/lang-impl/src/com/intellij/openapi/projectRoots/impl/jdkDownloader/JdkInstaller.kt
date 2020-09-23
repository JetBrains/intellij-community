// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.google.common.hash.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Urls
import com.intellij.util.io.*
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.Nls
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream
import kotlin.concurrent.withLock
import kotlin.math.absoluteValue

interface JdkInstallRequest {
  val item: JdkItem

  /**
   * The path where JDK is installed.
   * On macOS it is likely (depending on the JDK package)
   * to contain Contents/Home folders
   */
  val installDir: Path

  /**
   * The path on the disk where the installed JDK
   * would have the bin/java and bin/javac files.
   *
   * On macOs this path may differ from the [installDir]
   * if the JDK package follows the macOS Bundle layout
   */
  val javaHome: Path
}

private val JDK_INSTALL_LISTENER_EP_NAME = ExtensionPointName.create<JdkInstallerListener>("com.intellij.jdkDownloader.jdkInstallerListener")

interface JdkInstallerListener {
  /**
   * Executed at the moment, when a download process for
   * a given [request] is started
   */
  @JvmDefault
  fun onJdkDownloadStarted(request: JdkInstallRequest, project: Project?) { }

  /**
   * This event is executed when download process is finished,
   * for all possible outcomes, no matter it was a success or a failure
   */
  @JvmDefault
  fun onJdkDownloadFinished(request: JdkInstallRequest, project: Project?) { }
}

class JdkInstaller {
  companion object {
    @JvmStatic
    fun getInstance() = service<JdkInstaller>()
  }

  private val LOG = logger<JdkInstaller>()

  private operator fun File.div(path: String) = File(this, path).absoluteFile

  fun defaultInstallDir() : Path {
    val explicitHome = System.getProperty("jdk.downloader.home")
    if (explicitHome != null) {
      return Paths.get(explicitHome)
    }

    val home = Paths.get(FileUtil.toCanonicalPath(System.getProperty("user.home") ?: "."))
    return when {
      SystemInfo.isLinux   -> home.resolve(".jdks")
      //see https://youtrack.jetbrains.com/issue/IDEA-206163#focus=streamItem-27-3270022.0-0
      SystemInfo.isMac     -> home.resolve("Library/Java/JavaVirtualMachines")
      SystemInfo.isWindows -> home.resolve(".jdks")
      else -> error("Unsupported OS: ${SystemInfo.getOsNameAndVersion()}")
    }
  }

  fun defaultInstallDir(newVersion: JdkItem) : Path {
    val targetDir = defaultInstallDir().resolve(newVersion.installFolderName)
    var count = 1
    var uniqueDir = targetDir
    while (uniqueDir.exists()) {
      uniqueDir = targetDir.parent.resolve("${targetDir.fileName}-${count++}")
    }
    return uniqueDir.toAbsolutePath()
  }

  fun validateInstallDir(selectedPath: String): Pair<Path?, @Nls String?> {
    if (selectedPath.isBlank()) {
      return null to ProjectBundle.message("dialog.message.error.target.path.empty")
    }

    val targetDir = runCatching { Paths.get(FileUtil.expandUserHome(selectedPath)) }.getOrElse { t ->
      LOG.warn("Failed to resolve user path: $selectedPath. ${t.message}", t)
      return null to ProjectBundle.message("dialog.message.error.resolving.path")
    }

    if (Files.isRegularFile(targetDir)) {
      return null to ProjectBundle.message("dialog.message.error.target.path.exists.file")
    }
    if (Files.isDirectory(targetDir) && targetDir.toFile().listFiles()?.isNotEmpty() == true) {
      return null to ProjectBundle.message("dialog.message.error.target.path.exists.nonEmpty.dir")
    }

    return targetDir to null
  }

  /**
   * @see [JdkInstallRequest.javaHome] for the actual java home, it may not match the [JdkInstallRequest.installDir]
   */
  fun installJdk(request: JdkInstallRequest, indicator: ProgressIndicator?, project: Project?) {
    if (request is LocallyFoundJdk) {
      return
    }

    if (request is PendingJdkRequest) {
      request.tryStartInstallOrWait(indicator) {
        installJdkImpl(request, indicator, project)
      }
      return
    }

    LOG.error("Unexpected JdkInstallRequest: $request of type ${request.javaClass.name}")
    installJdkImpl(request, indicator, project)
  }

  /**
   * @see [JdkInstallRequest.javaHome] for the actual java home, it may not match the [JdkInstallRequest.installDir]
   */
  private fun installJdkImpl(request: JdkInstallRequest, indicator: ProgressIndicator?, project: Project?) {
    JDK_INSTALL_LISTENER_EP_NAME.forEachExtensionSafe { it.onJdkDownloadStarted(request, project) }

    val item = request.item
    indicator?.text = ProjectBundle.message("progress.text.installing.jdk.1", item.fullPresentationText)

    val targetDir = request.installDir
    val url = Urls.parse(item.url, false) ?: error("Cannot parse download URL: ${item.url}")
    if (!url.scheme.equals("https", ignoreCase = true)) {
      error("URL must use https:// protocol, but was: $url")
    }

    indicator?.text2 = ProjectBundle.message("progress.text2.downloading.jdk")
    val downloadFile = Paths.get(PathManager.getTempPath(), "jdk-${item.archiveFileName}")
    try {
      try {
        HttpRequests.request(item.url)
          .productNameAsUserAgent()
          .saveToFile(downloadFile.toFile(), indicator)
      }
      catch (t: Throwable) {
        if (t is ControlFlowException) throw t
        throw RuntimeException("Failed to download ${item.fullPresentationText} from $url. ${t.message}", t)
      }

      val sizeDiff = Files.size(downloadFile) - item.archiveSize
      if (sizeDiff != 0L) {
        throw RuntimeException("The downloaded ${item.fullPresentationText} has incorrect file size,\n" +
                               "the difference is ${sizeDiff.absoluteValue} bytes.\n" +
                               "Check your internet connection and try again later")
      }

      val actualHashCode = com.google.common.io.Files.asByteSource(downloadFile.toFile()).hash(Hashing.sha256()).toString()
      if (!actualHashCode.equals(item.sha256, ignoreCase = true)) {
        throw RuntimeException("Failed to verify SHA-256 checksum for ${item.fullPresentationText}\n\n" +
                               "The actual value is $actualHashCode,\n" +
                               "but expected ${item.sha256} was expected\n" +
                               "Check your internet connection and try again later")
      }

      indicator?.isIndeterminate = true
      indicator?.text2 = ProjectBundle.message("progress.text2.unpacking.jdk")

      try {
        val decompressor = item.packageType.openDecompressor(downloadFile)
        //handle cancellation via postProcessor (instead of inheritance)
        decompressor.postProcessor { indicator?.checkCanceled() }

        val fullMatchPath = item.packageRootPrefix.trim('/')
        if (!fullMatchPath.isBlank()) {
          decompressor.removePrefixPath(fullMatchPath)
        }
        decompressor.extract(targetDir)

        runCatching { writeMarkerFile(request) }
        runCatching { service<JdkInstallerStore>().registerInstall(item, targetDir) }
      }
      catch (t: Throwable) {
        if (t is ControlFlowException) throw t
        throw RuntimeException("Failed to extract ${item.fullPresentationText}. ${t.message}", t)
      }
    }
    catch (t: Throwable) {
      //if we were cancelled in the middle or failed, let's clean up
      targetDir.delete()
      markerFile(targetDir)?.delete()
      throw t
    }
    finally {
      runCatching { FileUtil.delete(downloadFile) }
      JDK_INSTALL_LISTENER_EP_NAME.forEachExtensionSafe { it.onJdkDownloadFinished(request, project) }
    }
  }

  private val myLock = ReentrantLock()
  private val myPendingDownloads = HashMap<JdkItem, PendingJdkRequest>()

  /**
   * Checks if we already have the requested JDK or the download is already running.
   * Returns different [JdkInstallRequest] implementations depending on the current
   * state, it is still required to call the [installJdk] on every of such.
   *
   * executed synchronously to prepare Jdk installation process, that would run in the future
   *
   * The [JdkInstallRequest] may have another [targetPath] if there is such JDK already installed,
   * or it is being installed right now
   */
  fun prepareJdkInstallation(jdkItem: JdkItem, targetPath: Path): JdkInstallRequest {
    val existingRequest = findAlreadyInstalledJdk(jdkItem)
    if (existingRequest != null) return existingRequest

    return myLock.withLock {
      myPendingDownloads.computeIfAbsent(jdkItem) { prepareJdkInstallationImpl(jdkItem, targetPath) }
    }
  }

  /**
   * This method does not check any existing or pending SDKs locally,
   * use [prepareJdkInstallation()] to avoid extra work if possible
   *
   * @see prepareJdkInstallation
   */
  fun prepareJdkInstallationDirect(jdkItem: JdkItem, targetPath: Path): JdkInstallRequest = prepareJdkInstallationImpl(jdkItem, targetPath)

  private fun prepareJdkInstallationImpl(jdkItem: JdkItem, targetPath: Path) : PendingJdkRequest {
    val (home, error) = validateInstallDir(targetPath.toString())
    if (home == null || error != null) {
      throw RuntimeException(error ?: "Invalid Target Directory")
    }

    val javaHome = jdkItem.resolveJavaHome(targetPath)
    Files.createDirectories(javaHome)

    val request = PendingJdkRequest(jdkItem, targetPath, javaHome)
    writeMarkerFile(request)
    return request
  }

  private fun markerFile(installDir: Path): Path? = installDir.parent?.resolve(".${installDir.fileName}.intellij")

  private fun writeMarkerFile(request: JdkInstallRequest) {
    val installDir = request.installDir
    val markerFile = markerFile(installDir) ?: return
    try {
      request.item.writeMarkerFile(markerFile)
    }
    catch (t: Throwable) {
      if (t is ControlFlowException) throw t
      LOG.warn("Failed to write marker file to $markerFile. ${t.message}", t)
    }
  }

  fun findJdkItemForInstalledJdk(jdkHome: String?): JdkItem? {
    try {
      if (jdkHome == null) return null
      val jdkPath = Paths.get(jdkHome)
      return findJdkItemForInstalledJdk(jdkPath)
    }
    catch (t: Throwable) {
      return null
    }
  }

  fun findJdkItemForInstalledJdk(jdkPath: Path?): JdkItem? {
    try {
      if (jdkPath == null) return null
      if (!jdkPath.isDirectory()) return null

      // Java package install dir have several folders up from it, e.g. Contents/Home on macOS
      val markerFile = generateSequence(jdkPath, { file -> file.parent })
                         .takeWhile { it.isDirectory() }
                         .take(5)
                         .mapNotNull { markerFile(it) }
                         .firstOrNull { it.isFile() } ?: return null

      val json = JdkListParser.readTree(markerFile.readBytes())
      return JdkListParser.parseJdkItem(json, JdkPredicate.createInstance())
    }
    catch (e: Throwable) {
      return null
    }
  }

  private fun findAlreadyInstalledJdk(feedItem: JdkItem) : JdkInstallRequest? {
    try {
      //TODO: we may track install locations nad use the data to scan more paths
      Files.list(defaultInstallDir()).use { list ->
        for (installDir in Stream.concat(list, service<JdkInstallerStore>().findInstallations(feedItem).stream())) {
          if (!installDir.isDirectory()) continue
          val item = findJdkItemForInstalledJdk(installDir) ?: continue
          if (item != feedItem) continue

          val jdkHome = item.resolveJavaHome(installDir)
          if (jdkHome.isDirectory() && JdkUtil.checkForJdk(jdkHome.toFile())) {
            return LocallyFoundJdk(feedItem, installDir, jdkHome)
          }
        }
      }
    } catch (t: Throwable) {
      if (t is ControlFlowException) throw t
      LOG.warn("Failed to scan for installed JDKs. ${t.message}", t)
    }

    return null
  }
}

private data class PendingJdkRequest(
  override val item: JdkItem,
  override val installDir: Path,
  override val javaHome: Path) : JdkInstallRequest {
  private val isRunning = AtomicBoolean(false)
  private val future = CompletableFuture<Unit>()


  @Volatile
  private var progressIndicator : ProgressIndicator? = null

  fun tryStartInstallOrWait(indicator: ProgressIndicator?, installAction: () -> Unit) {
    if (isRunning.compareAndSet(false, true)) {
      doRealDownload(indicator, installAction)
    } else {
      waitForDownload(indicator)
    }
  }

  private fun doRealDownload(indicator: ProgressIndicator?, installAction: () -> Unit) {
    progressIndicator = indicator
    try {
      installAction()
      future.complete(Unit)
    }
    catch (t: Throwable) {
      future.completeExceptionally(t)
      throw t
    }
    finally {
      progressIndicator = null
    }
  }

  private fun waitForDownload(indicator: ProgressIndicator?) {
    wrapProgressIfNeeded(indicator) {
      while (true) {
        indicator?.checkCanceled()
        try {
          future.get(100, TimeUnit.MILLISECONDS)
          return@wrapProgressIfNeeded
        }
        catch (t: TimeoutException) {
          continue
        }
        catch (e: InterruptedException) {
          throw ProcessCanceledException()
        }
        catch (e: CancellationException) {
          throw ProcessCanceledException()
        }
        catch (e: ExecutionException) {
          throw e.cause ?: e
        }
      }
    }
  }

  private fun wrapProgressIfNeeded(indicator: ProgressIndicator?, action: () -> Unit) {
    val parentProgress = progressIndicator as? ProgressIndicatorBase
    if (indicator == null || parentProgress == null) {
      return action()
    }

    val delegate = RelayUiToDelegateIndicator(indicator)
    parentProgress.addStateDelegate(delegate)
    try {
      return action()
    } finally {
      parentProgress.removeStateDelegate(delegate)
    }
  }

  override fun toString(): String {
    return "PendingJdkRequest(item=$item, installDir=$installDir)"
  }
}

private data class LocallyFoundJdk(
  override val item: JdkItem,
  override val installDir: Path,
  override val javaHome: Path) : JdkInstallRequest {

  override fun toString(): String {
    return "LocallyFoundJdk(item=$item, installDir=$installDir)"
  }
}


@Tag("installed-jdk")
class JdkInstallerStateEntry : BaseState() {
  var fullText by string()
  var versionText by string()
  var url by string()
  var sha256 by string()
  var installDir by string()
  var javaHomeDir by string()

  fun copyForm(item: JdkItem, targetPath: Path) {
    fullText = item.fullPresentationText
    versionText = item.versionPresentationText
    url = item.url
    sha256 = item.sha256
    installDir = targetPath.toAbsolutePath().toString()
    javaHomeDir = item.resolveJavaHome(targetPath).toAbsolutePath().toString()
  }

  val installPath get() = installDir?.let { Paths.get(it) }
  val javaHomePath get() = javaHomeDir?.let { Paths.get(it) }

  fun matches(item: JdkItem) : Boolean {
    if (fullText != item.fullPresentationText) return false
    if (versionText != item.versionPresentationText) return false
    if (url != item.url) return false
    if (sha256 != item.sha256) return false
    return true
  }
}

class JdkInstallerState : BaseState() {
  @get:XCollection
  var installedItems by list<JdkInstallerStateEntry>()
}

@State(name = "JdkInstallerHistory", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)], allowLoadInTests = true)
class JdkInstallerStore : SimplePersistentStateComponent<JdkInstallerState>(JdkInstallerState()) {
  private val lock = ReentrantLock()

  override fun loadState(state: JdkInstallerState) = lock.withLock {
    super.loadState(state)
  }

  fun registerInstall(jdkItem: JdkItem, targetPath: Path) = lock.withLock {
    state.installedItems.removeIf { it.installPath?.isDirectory() != null || it.matches(jdkItem) }
    state.installedItems.add(JdkInstallerStateEntry().apply { copyForm(jdkItem, targetPath) })
    state.intIncrementModificationCount()
  }

  fun findInstallations(jdkItem: JdkItem) : List<Path> = lock.withLock {
    state.installedItems.filter { it.matches(jdkItem) }.mapNotNull { it.installPath }.filter { it.isDirectory() }
  }

  fun listJdkInstallHomes() : List<Path> = lock.withLock {
    state.installedItems.mapNotNull { it.javaHomePath }
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<JdkInstallerStore>()
  }
}
