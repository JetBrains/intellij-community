// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("WebStarterSettings")

package com.intellij.ide.starters.remote

import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.io.Decompressor
import org.jetbrains.annotations.Nls
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.zip.ZipFile

open class WebStarterServerOptions(
  val frameworkVersions: List<WebStarterFrameworkVersion>,
  val dependencyCategories: List<WebStarterDependencyCategory>,
) : UserDataHolderBase() {

  fun <T> extractOption(key: Key<T>, handler: (T) -> Unit) {
    val value = getUserData(key)
    if (value != null) {
      handler.invoke(value)
    }
  }
}

val SERVER_NAME_KEY: Key<String> = Key.create("name")
val SERVER_GROUP_KEY: Key<String> = Key.create("group")
val SERVER_ARTIFACT_KEY: Key<String> = Key.create("artifact")
val SERVER_VERSION_KEY: Key<String> = Key.create("version")
val SERVER_PACKAGE_NAME_KEY: Key<String> = Key.create("packageName")
val SERVER_LANGUAGES: Key<List<StarterLanguage>> = Key.create("languages")
val SERVER_LANGUAGE_LEVEL_KEY: Key<StarterLanguageLevel> = Key.create("languageLevel")
val SERVER_LANGUAGE_LEVELS_KEY: Key<List<StarterLanguageLevel>> = Key.create("languageLevels")
val SERVER_PROJECT_TYPES: Key<List<StarterProjectType>> = Key.create("projectTypes")
val SERVER_APPLICATION_TYPES: Key<List<StarterAppType>> = Key.create("appTypes")
val SERVER_PACKAGING_TYPES: Key<List<StarterAppPackaging>> = Key.create("packagingTypes")

open class WebStarterFrameworkVersion(
  val id: String,
  @NlsSafe val title: String,
  val isDefault: Boolean
) {
  override fun toString(): String {
    return "WebStarterFrameworkVersion(version='$id', isDefault=$isDefault)"
  }
}

open class WebStarterDependencyCategory(
  @NlsSafe
  val title: String,
  val dependencies: List<WebStarterDependency>
) {
  open fun isAvailable(starterContext: WebStarterContext): Boolean = true

  override fun toString(): String {
    return "WebStarterDependencyCategory(title='$title')"
  }
}

open class WebStarterDependency(
  val id: String,
  @NlsSafe
  override val title: String,
  override val description: String? = null,
  override val links: List<LibraryLink> = emptyList(),
  override val isDefault: Boolean = false,
  override val isRequired: Boolean = false
) : LibraryInfo {
  override fun toString(): String {
    return "WebStarterDependency(id='$id', title='$title')"
  }
}

class WebStarterContextProvider(
  val moduleBuilder: WebStarterModuleBuilder,
  val wizardContext: WizardContext,
  val starterContext: WebStarterContext,
  val settings: StarterWizardSettings,
  val parentDisposable: Disposable
)

sealed class DependencyState

class DependencyUnavailable(
  @Nls(capitalization = Nls.Capitalization.Sentence)
  val message: String?,
  @NlsSafe
  val hint: String? = null
) : DependencyState()

object DependencyAvailable : DependencyState()

@Throws(IOException::class)
fun unzipSubfolder(tempZipFile: File, contentEntryDir: File) {
  var rootFolderName: String

  ZipFile(tempZipFile).use { jar ->
    val entries = jar.entries()
    if (!entries.hasMoreElements()) {
      throw UnexpectedArchiveStructureException("The archive is empty")
    }
    val rootFolders = HashSet<String>()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      val path = Paths.get(entry.name)
      if (path.nameCount > 0) {
        rootFolders.add(path.normalize().getName(0).toString())
      }
    }
    if (rootFolders.size != 1) {
      throw UnexpectedArchiveStructureException(
        "The archive should have 1 subdirectory, but has: " + rootFolders.joinToString(","))
    }
    rootFolderName = rootFolders.iterator().next()
  }

  Decompressor.Zip(tempZipFile)
    .removePrefixPath(rootFolderName)
    .extract(contentEntryDir)
}

class DownloadResult(
  val isZip: Boolean,
  val tempFile: File,
  val filename: String
)

internal class UnexpectedArchiveStructureException(message: String) : IOException(message)