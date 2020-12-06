/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache
import org.jetbrains.jps.model.java.JdkVersionDetector

@CompileStatic
class CompilationTasksImpl extends CompilationTasks {
  private final CompilationContext context
  private final PortableCompilationCache jpsCache

  CompilationTasksImpl(CompilationContext context) {
    this.context = context
    this.jpsCache = new PortableCompilationCache(context)
  }

  @Override
  void compileModules(List<String> moduleNames, List<String> includingTestsInModules) {
    if (context.options.useCompiledClassesFromProjectOutput) {
      context.messages.info("Compilation skipped, the compiled classes from the project output will be used")
      resolveProjectDependencies()
    }
    else if (jpsCache.canBeUsed) {
      context.messages.info("JPS remote cache will be used")
      jpsCache.downloadCacheAndCompileProject()
    }
    else if (context.options.pathToCompiledClassesArchive != null) {
      context.messages.info("Compilation skipped, the compiled classes from '${context.options.pathToCompiledClassesArchive}' will be used")
      resolveProjectDependencies()
    }
    else if (context.options.pathToCompiledClassesArchivesMetadata != null) {
      context.messages.info("Compilation skipped, the compiled classes from '${context.options.pathToCompiledClassesArchivesMetadata}' will be used")
      resolveProjectDependencies()
    }
    else {
      CompilationContextImpl.setupCompilationDependencies(context.gradle, context.options)
      def currentJdk = JdkUtils.currentJdk
      def jdkInfo = JdkVersionDetector.instance.detectJdkVersionInfo(currentJdk)
      if (jdkInfo.version.feature != 11) {
        context.messages.error("Build script must be executed under Java 11 to compile intellij project, but it's executed under Java $jdkInfo.version ($currentJdk)")
      }

      context.messages.progress("Compiling project")
      JpsCompilationRunner runner = new JpsCompilationRunner(context)
      try {
        if (moduleNames == null) {
          if (includingTestsInModules == null) {
            runner.buildAll()
          }
          else {
            runner.buildProduction()
          }
        }
        else {
          List<String> invalidModules = moduleNames.findAll { context.findModule(it) == null }
          if (!invalidModules.empty) {
            context.messages.warning("The following modules won't be compiled: $invalidModules")
          }
          runner.buildModules(moduleNames.collect { context.findModule(it) }.findAll { it != null })
        }

        if (includingTestsInModules != null) {
          for (String moduleName : includingTestsInModules) {
            runner.buildModuleTests(context.findModule(moduleName))
          }
        }
      }
      catch (Throwable e) {
        context.messages.error("Compilation failed with exception: $e", e)
      }
    }
  }

  @Override
  void buildProjectArtifacts(Collection<String> artifactNames) {
    if (!artifactNames.isEmpty()) {
      boolean buildIncludedModules = !context.options.useCompiledClassesFromProjectOutput &&
                                     context.options.pathToCompiledClassesArchive == null &&
                                     context.options.pathToCompiledClassesArchivesMetadata == null
      try {
        new JpsCompilationRunner(context).buildArtifacts(artifactNames, buildIncludedModules)
      }
      catch (Throwable e) {
        context.messages.error("Building project artifacts failed with exception: $e", e)
      }
    }
  }

  @Override
  void resolveProjectDependencies() {
    new JpsCompilationRunner(context).resolveProjectDependencies()
  }

  @Override
  void compileAllModulesAndTests() {
    compileModules(null, null)
  }

  @Override
  void resolveProjectDependenciesAndCompileAll() {
    resolveProjectDependencies()
    context.compilationData.statisticsReported = false
    compileAllModulesAndTests()
  }
}
