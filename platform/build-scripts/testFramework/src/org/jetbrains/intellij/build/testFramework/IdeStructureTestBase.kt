// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.ModuleStructureValidator
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector

abstract class IdeStructureTestBase {
  @Rule
  @JvmField
  val errorCollector: ErrorCollector = ErrorCollector()

  protected abstract fun createProductProperties(homePath: String): ProductProperties
  protected abstract fun createBuildTools(): ProprietaryBuildTools
  protected abstract val missingModulesException: Set<String>

  private fun createBuildContext(): BuildContext {
    val homePath = PathManager.getHomePathFor(javaClass)!!
    val productProperties = createProductProperties(homePath)
    return createBuildContext(homePath, productProperties, createBuildTools(), false, "$homePath/community")
  }

  @Test
  fun moduleStructureValidation() {
    val buildContext = createBuildContext()
    val jarBuilder = DistributionJARsBuilder(buildContext, emptySet())

    println("Packed modules:")
    val moduleJars = jarBuilder.platform.moduleJars
    val module2Jar = moduleJars.entrySet().flatMap { it.value.map { e -> e to it.key } }.toMap()
    for (kv in module2Jar.entries.sortedBy { it.key }) {
      println("  ${kv.key} ${kv.value}")
    }

    val validator = ModuleStructureValidator(buildContext, moduleJars)
    val errors = validator.validate()
    for (error in errors) {
      errorCollector.addError(IllegalStateException(error))
    }
  }

  @Test
  fun moduleClosureValidation() {
    val buildContext = createBuildContext()
    val jarBuilder = DistributionJARsBuilder(buildContext, emptySet())
    val exceptions = missingModulesException
    val activeExceptions = mutableSetOf<String>()

    val module2Jar = jarBuilder.platform.jarToIncludedModuleNames.flatMap { it.value.map { e -> e to it.key } }.toMap()
    for (kv in module2Jar.entries.sortedBy { it.key }) {
      val module = buildContext.findRequiredModule(kv.key)
      for (dependency in module.dependenciesList.dependencies) {
        if (dependency is JpsModuleDependency) {
          val dependencyExtension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependency)!!
          if (dependencyExtension.scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
            val moduleDependency = dependency.module!!
            if (!module2Jar.containsKey(moduleDependency.name)) {
              if (exceptions.contains(moduleDependency.name)) {
                activeExceptions.add(moduleDependency.name)
              } else {
                val message = "${buildContext.productProperties.productCode} (${javaClass.simpleName}): missing module from the product layout '${moduleDependency.name}' referenced from '${module.name}' scope ${dependencyExtension.scope}"
                errorCollector.addError(IllegalStateException(message))
              }
            }
          }
        }
      }
    }

    for (moduleName in exceptions.minus(activeExceptions)) {
      errorCollector.addError(IllegalStateException("${buildContext.productProperties.productCode} (${javaClass.simpleName}): module '$moduleName' is mentioned in ${::missingModulesException.name}, but it was not used. Please remove it from the list"))
    }
  }
}