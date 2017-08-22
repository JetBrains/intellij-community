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
/**
 * @author nik
 */
@CompileStatic
class CompilationTasksImpl extends CompilationTasks {
  private final CompilationContext context

  CompilationTasksImpl(CompilationContext context) {
    this.context = context
  }

  @Override
  void compileModules(List<String> moduleNames, List<String> includingTestsInModules) {
    if (context.options.useCompiledClassesFromProjectOutput) {
      context.messages.info("Compilation skipped, the compiled classes from the project output will be used")
      resolveProjectDependencies()
      return
    }
    if (context.options.pathToCompiledClassesArchive != null) {
      context.messages.info("Compilation skipped, the compiled classes from '${context.options.pathToCompiledClassesArchive}' will be used")
      resolveProjectDependencies()
      return
    }

    CompilationContextImpl.setupCompilationDependencies(context.gradle)

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

  @Override
  void resolveProjectDependencies() {
    new JpsCompilationRunner(context).resolveProjectDependencies()
  }

  @Override
  void compileAllModulesAndTests() {
    compileModules(null, null)
  }
}
