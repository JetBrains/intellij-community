// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local

import com.intellij.ide.starters.shared.LibraryInfo
import com.intellij.ide.starters.shared.LibraryLink
import com.intellij.ide.starters.shared.StarterWizardSettings
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.net.URL
import javax.swing.Icon

data class Dependency(
  val group: String,
  val artifact: String,
  val version: String
)

data class DependencyConfig(
  val version: String,
  val properties: Map<String, String>,
  val dependencies: List<Dependency>
) {
  fun getVersion(group: String, artifact: String): String? {
    return dependencies.find { it.group == group && it.artifact == artifact }?.version
  }
}

data class LibraryCategory(
  val id: String,
  val icon: Icon?,
  @Nls val title: String,
  @Nls val description: String
)

class Library (
  val id: String,
  val icon: Icon?,
  @NlsSafe
  override val title: String,
  @Nls
  override val description: String,
  val group: String?,
  val artifact: String?,
  override val links: List<LibraryLink> = emptyList(),
  val category: LibraryCategory? = null,
  override val isRequired: Boolean = false,
  override val isDefault: Boolean = false,
  val includesLibraries: Set<String> = emptySet()
) : LibraryInfo {
  override fun toString(): String {
    return "Library($id)"
  }
}

data class Starter(
  val id: String,
  val title: String,
  val versionConfigUrl: URL,
  val libraries: List<Library>
)

data class StarterPack(
  val defaultStarterId: String,
  val starters: List<Starter>
)

class StarterContextProvider(
  val moduleBuilder: StarterModuleBuilder,
  val parentDisposable: Disposable,
  val starterContext: StarterContext,
  val wizardContext: WizardContext,
  val settings: StarterWizardSettings,
  val starterPackProvider: () -> StarterPack
)