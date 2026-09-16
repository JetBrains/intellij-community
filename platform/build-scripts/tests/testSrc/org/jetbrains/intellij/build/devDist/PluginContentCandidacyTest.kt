// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * The offers of [derivePluginContentCandidacy] for a member the layout states and the plugin's own `<content>` does not.
 *
 * Every case builds one in-memory project: the plugin's main module and one member with a descriptor of its own. The
 * residue states the member, the way the layout facts do.
 */
class PluginContentCandidacyTest {
  @Test
  fun `a layout member with no named jar is offered at the convention's jar`(@TempDir root: Path) {
    // An `auto` layout child is the case: the layout states no jar for it, and the convention gives the member
    // `modules/<member>.jar`. A plain `withModule(name)` is not this case, because `layoutResidueOf` states the main
    // jar for it.
    val candidacy = derive(root = root, residue = PluginContentResidue(extraMembers = setOf(MEMBER)))

    assertThat(candidacy.memberPaths).isEqualTo(mapOf(MEMBER to "modules/$MEMBER.jar"))
    assertThat(candidacy.offers.map { it.moduleName to it.relativeOutputFile }).containsExactly(MEMBER to "modules/$MEMBER.jar")
    assertThat(candidacy.offers.single().libraries).isEmpty()
    assertThat(candidacy.vetoes).isEmpty()
  }

  @Test
  fun `a layout member the layout names a jar for keeps its path and gets no offer`(@TempDir root: Path) {
    val residue = PluginContentResidue(extraMembers = setOf(MEMBER), memberJars = mapOf(MEMBER to setOf("custom.jar")))

    val candidacy = derive(root = root, residue = residue)

    // The path is the convention's answer, and the jar composition puts the stated jar over it.
    assertThat(candidacy.memberPaths).isEqualTo(mapOf(MEMBER to "modules/$MEMBER.jar"))
    assertThat(candidacy.offers).isEmpty()
    assertThat(candidacy.vetoes).isEmpty()
  }

  @Test
  fun `a content member the layout packs into a flat jar of another name gets no offer`(@TempDir root: Path) {
    // `intellij.gradle.toolingExtension` in `intellij.gradle.plugin` is the case: an embedded member whose layout jar is
    // `gradle-tooling-extension-api.jar`. The plugin never packs the member's own jar, so it vouches for none.
    val closure = WalkedContentModules(moduleNames = listOf(MEMBER), loadingRules = mapOf(MEMBER to EMBEDDED_LOADING_RULE), unresolvedIncludes = emptyList())
    val renamed = derive(root = root, residue = PluginContentResidue(memberJars = mapOf(MEMBER to setOf("renamed.jar"))), closure = closure)
    assertThat(renamed.memberPaths).isEqualTo(mapOf(MEMBER to "$MEMBER.jar"))
    assertThat(renamed.offers).isEmpty()
    assertThat(renamed.vetoes).isEmpty()

    // A jar under a subdirectory is a second jar beside the member's own, and a flat jar of the member's own name is
    // the member's own jar. Both keep the offer.
    for (path in listOf("nested/renamed.jar", "$MEMBER.jar")) {
      val kept = derive(root = root, residue = PluginContentResidue(memberJars = mapOf(MEMBER to setOf(path))), closure = closure)
      assertThat(kept.offers.map { it.moduleName }).describedAs(path).containsExactly(MEMBER)
    }
  }

  @Test
  fun `a library whose every file an earlier library of the member brings merges nothing`(@TempDir root: Path) {
    // `intellij.ml.llm.libraries.grazie.cloud` is the case: the one file of `ai.grazie.api.gateway.jvm` is a file of
    // `ai.grazie.api.gateway.client.jvm` too, and the build copies a file once per target jar.
    val project = JpsElementFactory.getInstance().createModel().project
    val member = project.addModule(MEMBER, JpsJavaModuleType.INSTANCE)
    addModuleLibrary(member, "alpha", root.resolve("alpha.jar"), root.resolve("shared.jar"))
    addModuleLibrary(member, "beta", root.resolve("shared.jar"))
    addModuleLibrary(member, "gamma", root.resolve("shared.jar"), root.resolve("gamma.jar"))

    assertThat(productionModuleLibraryNames(member)).containsExactly("alpha", "gamma")
  }

  private fun addModuleLibrary(module: JpsModule, name: String, vararg files: Path) {
    val library = module.addModuleLibrary(name, JpsJavaLibraryType.INSTANCE)
    for (file in files) {
      library.addRoot(JpsPathUtil.pathToUrl(file.toString()), JpsOrderRootType.COMPILED)
    }
    val dependency = module.dependenciesList.addLibraryDependency(library)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.COMPILE
  }

  /** The candidacy of the demo plugin over a project holding its main module and [MEMBER], with [residue] stated. */
  private fun derive(root: Path, residue: PluginContentResidue, closure: WalkedContentModules? = null): DerivedPluginCandidacy {
    val project = JpsElementFactory.getInstance().createModel().project
    project.addModule(MAIN_MODULE, JpsJavaModuleType.INSTANCE)
    addMemberWithDescriptor(project = project, root = root)
    val findModule = project::findModuleByName
    return derivePluginContentCandidacy(
      mainModule = MAIN_MODULE,
      mainJarName = "demo.jar",
      findModule = findModule,
      frontend = FrontendCompatibility(roots = emptySet(), findModule = findModule),
      residue = residue,
      closure = closure,
    )
  }

  /**
   * Adds [MEMBER] with one resource root that holds its own descriptor.
   *
   * The descriptor states no `package` attribute, so the convention gives the member a jar of its own.
   */
  private fun addMemberWithDescriptor(project: JpsProject, root: Path) {
    val resources = root.resolve("resources").createDirectories()
    resources.resolve("$MEMBER.xml").writeText("<idea-plugin/>")
    val member = project.addModule(MEMBER, JpsJavaModuleType.INSTANCE)
    member.addSourceRoot(JpsPathUtil.pathToUrl(resources.toString()), JavaResourceRootType.RESOURCE)
  }

  private companion object {
    const val MAIN_MODULE: String = "intellij.demo"
    const val MEMBER: String = "intellij.demo.core"
  }
}
