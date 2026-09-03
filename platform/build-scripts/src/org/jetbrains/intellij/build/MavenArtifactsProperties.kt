// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.apache.maven.model.Model
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.maven.GeneratedMavenArtifacts
import org.jetbrains.intellij.build.impl.maven.MavenArtifactDependency
import org.jetbrains.intellij.build.impl.maven.MavenCoordinates
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

/**
 * Specifies how Maven artifacts for IDE modules should be generated.
 * Public artifacts are generated under [BuildPaths.artifactDir]/maven-artifacts directory.
 * Proprietary artifacts are generated under [BuildPaths.artifactDir]/proprietary-maven-artifacts directory.
 * @see ProductProperties.mavenArtifacts
 * @see org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder.generateMavenArtifacts
 */
class MavenArtifactsProperties {
  /**
   * If `true` Maven artifacts are generated for all modules included in the IDE distribution.
   */
  var forIdeModules: Boolean = false

  /**
   * If `true` Maven artifacts are generated for all library modules.
   * @see org.jetbrains.intellij.build.impl.libraries.isLibraryModule
   */
  var publishLibraryModules: Boolean = false

  /**
   * Names of additional modules for which Maven artifacts should be generated.
   */
  var additionalModules: PersistentList<String> = persistentListOf()

  /**
   * Names of modules for which Maven artifacts should be generated, that will create all its module-dependencies in a single jar.
   *
   * Initially, it's introduced for having `util-base` artifact which will include `util-rt` in it to avoid JPMS package-split.
   */
  var squashedModules: PersistentList<String> = persistentListOf()

  /**
   * Names of proprietary modules for which Maven artifacts should be generated.
   *
   *  <p>
   *  Note: Intended only for private Maven repository publication.
   *  </p>
   */
  var proprietaryModules: PersistentList<String> = persistentListOf()

  /**
   * Extra Maven "aggregator" artifacts to generate — each is a single pom.xml (packaging=pom, no jar)
   * that declares runtime-scope &lt;dependency&gt; entries on every Maven artifact produced by this build
   * matching the spec's filter. Downstream Maven consumers can depend on the aggregator with
   * &lt;type&gt;pom&lt;/type&gt; (Maven) or `@pom` classifier (Gradle) to transitively pull the entire set.
   *
   * Typical use: expose a single entry point for downstream projects that need the IDE's full runtime
   * classpath — including modules loaded at startup via the product descriptor that aren't reachable
   * as JPS dependencies.
   */
  var aggregatorPomArtifacts: PersistentList<AggregatorPomSpec> = persistentListOf()

  /**
   * A predicate which returns `true` for modules which sources should be published as Maven artifacts.
   */
  var publishSourcesFilter: (JpsModule, BuildContext) -> Boolean = { module, context ->
    module.contentRootsList.urls.all { Path.of(JpsPathUtil.urlToPath(it)).startsWith(context.paths.communityHomeDir) }
  }

  /**
   * A predicate which returns `true` for modules which Maven artifacts should be validated according to https://central.sonatype.org/publish/requirements
   */
  @ApiStatus.Internal
  var validateForMavenCentralPublication: (JpsModule) -> Boolean = { false }

  @ApiStatus.Internal
  var patchCoordinates: (JpsModule, MavenCoordinates) -> MavenCoordinates = { _, coordinates -> coordinates }

  @ApiStatus.Internal
  var patchDependencies: (JpsModule, List<MavenArtifactDependency>) -> List<MavenArtifactDependency> = { _, dependencies -> dependencies }

  /**
   * Returns `true` when the POM of the first module inlines the second module like a library module:
   * the library dependencies of the second module replace its own artifact.
   * A library module with a source root is published as an artifact of its own,
   * see [org.jetbrains.intellij.build.impl.libraries.isLibraryModule].
   * A module published to Maven Central cannot depend on that artifact.
   */
  @ApiStatus.Internal
  var inlineModuleDependency: (module: JpsModule, dependency: JpsModule) -> Boolean = { _, _ -> false }

  @ApiStatus.Internal
  var addPomMetadata: (JpsModule, Model) -> Unit = { _, _ -> }

  @ApiStatus.Internal
  var isJavadocJarRequired: (JpsModule) -> Boolean = { false }

  @ApiStatus.Internal
  var validate: suspend (BuildContext, Collection<GeneratedMavenArtifacts>) -> Unit = { _, _ -> }
}

/**
 * Specifies a Maven aggregator artifact: a pom.xml with `packaging=pom` (no jar) whose `<dependencies>`
 * are populated from Maven artifacts produced during the same build.
 *
 * @param groupId Maven groupId for the aggregator pom.
 * @param artifactId Maven artifactId for the aggregator pom.
 * @param description Optional `<description>` for the generated pom.
 * @param includeModule Predicate over published JPS module names. Returning `true` includes that
 *   module's artifact in the aggregator's `<dependencies>`. Defaults to including everything.
 */
@ApiStatus.Internal
class AggregatorPomSpec(
  val groupId: String,
  val artifactId: String,
  val description: String? = null,
  val includeModule: (moduleName: String) -> Boolean = { true },
)
