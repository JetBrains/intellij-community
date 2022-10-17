// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.ModuleStructureValidator
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path
import java.util.*

@ExtendWith(SoftAssertionsExtension::class)
abstract class IdeStructureTestBase {
  protected open val projectHome: Path
    get() = Path.of(PathManager.getHomePathFor(javaClass)!!)

  protected abstract fun createProductProperties(projectHome: Path): ProductProperties
  protected abstract fun createBuildTools(): ProprietaryBuildTools
  protected open val missingModulesException: Set<MissingModuleException>
    get() = emptySet()

  data class MissingModuleException(val fromModule: String, val toModule: String, val scope: JpsJavaDependencyScope)

  private fun createBuildContext(): BuildContext {
    val productProperties = createProductProperties(projectHome)
    return runBlocking(Dispatchers.Default) {
      createBuildContext(homePath = projectHome,
                                    productProperties = productProperties,
                                    buildTools = createBuildTools(),
                                    skipDependencySetup = false,
                                    communityHomePath = IdeaProjectLoaderUtil.guessCommunityHome(javaClass))

    }
  }

  @Test
  fun moduleStructureValidation(softly: SoftAssertions) {
    val context = createBuildContext()
    val jarBuilder = DistributionJARsBuilder(context, emptySet())

    println("Packed modules:")
    val moduleToJar = jarBuilder.state.platform.jarToModules.entries.asSequence()
      .flatMap { it.value.map { e -> e to it.key } }
      .groupBy(keySelector = { it.first }, valueTransform = { it.second })
      .toSortedMap()
    for (kv in moduleToJar) {
      println("  ${kv.key} ${kv.value}")
    }

    val validator = ModuleStructureValidator(context, jarBuilder.state.platform.jarToModules)
    val errors = validator.validate()
    for (error in errors) {
      softly.collectAssertionError(error)
    }
  }

  @Test
  fun moduleClosureValidation(softly: SoftAssertions) {
    val buildContext = createBuildContext()
    val jarBuilder = DistributionJARsBuilder(buildContext, emptySet())
    val exceptions = missingModulesException
    val activeExceptions = mutableSetOf<MissingModuleException>()

    val moduleToJar = jarBuilder.state.platform.jarToModules.asSequence()
      .flatMap { it.value.map { e -> e to it.key } }
      .toMap(TreeMap())
    for (kv in moduleToJar) {
      val module = buildContext.findRequiredModule(kv.key)
      for (dependency in module.dependenciesList.dependencies) {
        if (dependency !is JpsModuleDependency) {
          continue
        }

        val dependencyExtension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependency)!!
        if (!dependencyExtension.scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
          continue
        }

        val moduleDependency = dependency.module!!
        if (!moduleToJar.containsKey(moduleDependency.name)) {
          val missingModuleException = MissingModuleException(module.name, moduleDependency.name, dependencyExtension.scope)
          if (exceptions.contains(missingModuleException)) {
            activeExceptions.add(missingModuleException)
          }
          else {
            val message = "${buildContext.productProperties.productCode} (${javaClass.simpleName}): missing module from the product layout '${moduleDependency.name}' referenced from '${module.name}' scope ${dependencyExtension.scope}"
            softly.fail<Unit>(message)
          }
        }
      }
    }

    for (moduleName in exceptions.minus(activeExceptions)) {
      softly.fail<Unit>("${buildContext.productProperties.productCode} (${javaClass.simpleName}): module '$moduleName' is mentioned in ${::missingModulesException.name}, but it was not used. Please remove it from the list")
    }
  }
}