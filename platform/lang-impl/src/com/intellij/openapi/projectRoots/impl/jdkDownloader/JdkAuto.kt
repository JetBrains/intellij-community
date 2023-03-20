// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.*
import com.intellij.openapi.roots.ui.configuration.SdkDetector.DetectedSdkListener
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.io.File

private class JdkAutoHint: BaseState() {
  val name by string()
  val path: String? by string()
  val version by string()

  @get:XCollection
  val includeJars by list<String>()
}

private class JdkAutoHints : BaseState() {
  @get:XCollection
  val jdks by list<JdkAutoHint>()
}

private class JdkAutoHintService(private val project: Project) : SimplePersistentStateComponent<JdkAutoHints>(JdkAutoHints()) {
  override fun loadState(state: JdkAutoHints) {
    super.loadState(state)

    UnknownSdkTracker.getInstance(project).updateUnknownSdks()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) : JdkAutoHintService = project.service()
  }
}

private class JarSdkConfigurator(val extraJars: List<String>) : UnknownSdkFixConfigurator {
  override fun configureSdk(sdk: Sdk) {
    val sdkModificator = sdk.sdkModificator
    for (path in extraJars) {
      val extraJar = resolveExtraJar(sdk, path)
      if (extraJar != null) {
        sdkModificator.addRoot(extraJar, OrderRootType.CLASSES)
        LOG.info("Jar '$path' has been added to sdk '${sdk.name}'")
      }
      else {
        LOG.warn("Cant resolve path '$path' for jdk home '${sdk.homeDirectory}'")
      }
    }
    sdkModificator.commitChanges()
  }

  private fun resolveExtraJar(sdk: Sdk, path: String): VirtualFile? {
    val homeDirectory = sdk.homeDirectory ?: return null
    val file = homeDirectory.findFileByRelativePath(path) ?: return null
    return JarFileSystem.getInstance().getJarRootForLocalFile(file)
  }
}

private val LOG = logger<JdkAuto>()

class JdkAuto : UnknownSdkResolver, JdkDownloaderBase {
  override fun supportsResolution(sdkTypeId: SdkTypeId) = notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType().value(sdkTypeId)

  override fun createResolver(project: Project?, indicator: ProgressIndicator): UnknownSdkLookup? {
    if (!Registry.`is`("jdk.auto.setup")) return null
    if (ApplicationManager.getApplication().isUnitTestMode) return null
    return createResolverImpl(project, indicator)
  }

  fun createResolverImpl(project: Project?, indicator: ProgressIndicator): UnknownSdkLookup? {
    val sdkType = SdkType.getAllTypes()
                    .singleOrNull(notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType()::value) ?: return null

    return object : UnknownSdkLookup {
      val projectWslDistribution by lazy {
        project?.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) }
      }

      val projectInWsl by lazy {
        project?.basePath?.let { WslPath.isWslUncPath(it) } == true
      }

      val lazyDownloadModel: List<JdkItem> by lazy {
        indicator.pushState()
        indicator.text = ProjectBundle.message("progress.text.downloading.jdk.list")
        try {
          val jdkPredicate = when {
            projectInWsl -> JdkPredicate.forWSL()
            else -> JdkPredicate.default()
          }

          JdkListDownloader.getInstance().downloadModelForJdkInstaller(indicator, jdkPredicate)
        } catch(e: ProcessCanceledException) {
          throw e
        } catch (t: Throwable) {
          LOG.warn("JdkAuto has failed to download the list of available JDKs. " + t.message, t)
          listOf()
        } finally {
          indicator.popState()
        }
      }

      private fun resolveHint(sdk: UnknownSdk) : JdkAutoHint? {
        if (sdk.sdkType != sdkType) return null

        project ?: return null
        val sdkName = sdk.sdkName ?: return null

        return JdkAutoHintService
          .getInstance(project)
          .state
          .jdks.singleOrNull { it.name.equals(sdkName, ignoreCase = true) &&
                               it.path?.let { path -> projectInWsl == WslPath.isWslUncPath(path) } ?: false }
      }

      private fun parseSdkRequirement(sdk: UnknownSdk): JdkRequirement? {
        val hint = resolveHint(sdk)

        val namePredicate = hint?.version?.trim()?.toLowerCase()?.nullize(true)
                            ?: JavaVersion.tryParse(sdk.expectedVersionString)?.toFeatureMinorUpdateString()
                            ?: sdk.sdkName

        return JdkRequirements.parseRequirement(
          namePredicate = namePredicate,
          versionStringPredicate = sdk.sdkVersionStringPredicate,
          homePredicate = sdk.sdkHomePredicate
        )
      }

      private fun resolveHintPath(sdk: UnknownSdk, indicator: ProgressIndicator) :UnknownSdkLocalSdkFix? {
        val hint = resolveHint(sdk)
        val path = hint?.path ?: return null
        indicator.text = ProjectBundle.message("progress.text.resolving.hint.path", path)
        if (!File(path).isDirectory) return null

        val version = runCatching {
          sdkType.getVersionString(path)
        }.getOrNull() ?: return null

        return object : UnknownSdkLocalSdkFix, UnknownSdkFixConfigurator by JarSdkConfigurator(hint.includeJars) {
          override fun getExistingSdkHome(): String = path
          override fun getVersionString(): String = version
          override fun getSuggestedSdkName(): @NotNull String {
            val hintPath = hint.path ?: return ""
            return sdkType.suggestSdkName(null, hintPath)
          }
          override fun toString() = "UnknownSdkLocalSdkFix{hint $version, $path}"
        }
      }

      override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkDownloadableSdkFix? {
        if (sdk.sdkType != sdkType) return null

        val req = parseSdkRequirement(sdk) ?: return null
        LOG.info("Looking for a possible download for ${sdk.sdkType.presentableName} with name ${sdk}")

        //we select the newest matching version for a possible fix
        val jdkToDownload = lazyDownloadModel
                              .asSequence()
                              .filter { req.matches(it) }
                              .filter { CpuArch.fromString(it.arch) == CpuArch.CURRENT }
                              .mapNotNull {
                                val v = JavaVersion.tryParse(it.versionString)
                                if (v != null) {
                                  it to v
                                }
                                else null
                              }.maxByOrNull { it.second }
                              ?.first ?: return null

        val jarConfigurator = JarSdkConfigurator(resolveHint(sdk)?.includeJars ?: listOf())

        return object: UnknownSdkDownloadableSdkFix, UnknownSdkFixConfigurator by jarConfigurator {
          override fun getVersionString() = jdkToDownload.versionString
          override fun getPresentableVersionString() = jdkToDownload.presentableVersionString

          override fun getDownloadDescription() = jdkToDownload.fullPresentationText

          override fun createTask(indicator: ProgressIndicator): SdkDownloadTask {
            val jdkInstaller = JdkInstaller.getInstance()
            val homeDir = jdkInstaller.defaultInstallDir(jdkToDownload, projectWslDistribution)
            val request = jdkInstaller.prepareJdkInstallation(jdkToDownload, homeDir)
            return newDownloadTask(request, project)
          }

          override fun toString() = "UnknownSdkDownloadableFix{${jdkToDownload.fullPresentationText}, wsl=${projectWslDistribution}}"
        }
      }

      val lazyLocalJdks by lazy {
        indicator.text = ProjectBundle.message("progress.text.detecting.local.jdks")
        val result = mutableListOf<JavaLocalSdkFix>()

        SdkDetector.getInstance().detectSdks(sdkType, indicator, object : DetectedSdkListener {
          override fun onSdkDetected(type: SdkType, version: String, home: String) {
            val javaVersion = JavaVersion.tryParse(version) ?: return
            val suggestedName = JdkUtil.suggestJdkName(version) ?: return
            result += JavaLocalSdkFix(home, javaVersion, suggestedName)
          }
        })

        result.also {
          LOG.info(
            result.joinToString(prefix = "The following local JDKs were found: ")
            { "[${it.existingSdkHome}, ${it.presentableVersionString}, ${it.suggestedSdkName}]" }
          )
        }
      }

      override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator): UnknownSdkLocalSdkFix? {
        if (sdk.sdkType != sdkType) return null

        val hintMatch = resolveHintPath(sdk, indicator)
        if (hintMatch != null) {
          LOG.info("Found hint path for local SDK: path ${hintMatch.existingSdkHome}")
          return hintMatch
        }

        val req = parseSdkRequirement(sdk) ?: run {
          LOG.info("Failed to parse unknown SDK requirement ${sdk}")
          return null
        }
        LOG.info("Looking for a local SDK for ${sdk.sdkType.presentableName} with name ${sdk}")

        fun List<JavaLocalSdkFix>.pickBestMatch() = this.maxByOrNull { it.version }

        val localSdkFix = tryUsingExistingSdk(req, sdk.sdkType, indicator).filterByWsl().pickBestMatch()
                          ?: lazyLocalJdks.filter { req.matches(it) }.filterByWsl().pickBestMatch()

        return localSdkFix?.copy(includeJars = resolveHint(sdk)?.includeJars ?: listOf())
      }

      private fun tryUsingExistingSdk(req: JdkRequirement, sdkType: SdkType, indicator: ProgressIndicator): List<JavaLocalSdkFix> {
        indicator.text = ProjectBundle.message("progress.text.checking.existing.jdks")

        val result = mutableListOf<JavaLocalSdkFix>()
        for (it in runReadAction { ProjectJdkTable.getInstance().allJdks }) {
          if (it.sdkType != sdkType) continue

          val homeDir = runCatching { it.homePath }.getOrNull() ?: continue
          val versionString = runCatching { it.versionString }.getOrNull() ?: continue
          val version = runCatching { JavaVersion.tryParse(versionString) }.getOrNull() ?: continue
          val suggestedName = runCatching { JdkUtil.suggestJdkName(versionString) }.getOrNull() ?: continue

          if (it !is MockSdk && runCatching {
              val homePath = it.homePath
              homePath != null && sdkType.isValidSdkHome(homePath) }.getOrNull() != true) continue
          if (runCatching { req.matches(it) }.getOrNull() != true) continue

          result += JavaLocalSdkFix(homeDir, version, suggestedName, prototype = it)
        }

        return result
      }

      private fun List<JavaLocalSdkFix>.filterByWsl(): List<JavaLocalSdkFix> {
        return filter { WslPath.isWslUncPath(it.homeDir) == projectInWsl }
      }
    }
  }

  private data class JavaLocalSdkFix(
    val homeDir: String,
    val version: JavaVersion,
    val suggestedName: String,
    val includeJars: List<String> = emptyList(),
    val prototype: Sdk? = null
  ) : UnknownSdkLocalSdkFix, UnknownSdkFixConfigurator by JarSdkConfigurator(includeJars) {

    override fun getExistingSdkHome() = homeDir
    override fun getVersionString() = JdkVersionDetector.formatVersionString(version)
    override fun getPresentableVersionString() = version.toFeatureMinorUpdateString()
    override fun getSuggestedSdkName() : String = suggestedName
    override fun getRegisteredSdkPrototype(): Sdk? = prototype
    override fun toString() = "UnknownSdkLocalSdkFix{$presentableVersionString, dir=$homeDir}"
  }
}
