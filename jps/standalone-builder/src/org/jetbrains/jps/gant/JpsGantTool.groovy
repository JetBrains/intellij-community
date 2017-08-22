/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.gant

import org.apache.tools.ant.AntClassLoader
import org.codehaus.gant.GantBinding
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader

/**
 * @deprecated use classes from {@link org.jetbrains.intellij.build} package in platform-build-scripts module for building IDEs based
 * on IntelliJ Platform; these scripts use type-safe org.jetbrains.intellij.build.impl.LayoutBuilder to lay out modules output by JARs and
 * directories.
 *
 * @author nik
 */
final class JpsGantTool {
  JpsGantTool(GantBinding binding) {
    JpsModel model = JpsElementFactory.getInstance().createModel()
    JpsProject project = model.project
    binding.setVariable("project", project)
    binding.setVariable("global", model.global)
    def builder = new JpsGantProjectBuilder(binding.ant.project, model)
    binding.setVariable("projectBuilder", builder)
    binding.setVariable("loadProjectFromPath", {String path ->
      loadProject(path, model, builder)
    })
    binding.setVariable("addModule", {File imlFile ->
      return addModule(imlFile, model, builder)
    })

    binding.setVariable("jdk", {Object[] args ->
      if (!(args.length in [2,3])) {
        builder.error("expected 2 to 3 parameters for jdk() but ${args.length} found")
      }
      Closure initializer = args.length > 2 ? (Closure)args[2] : {}
      return createJavaSdk(model.global, (String)args[0], (String)args[1], initializer)
    })

    binding.setVariable("layout", {String dir, Closure body ->
      def layoutInfo = new LayoutInfo()

      ["module", "moduleTests", "zip", "dir"].each {tag ->
        binding.setVariable(tag, {Object[] args ->
          if (args.length == 1) {
            binding.ant."$tag"(name: args[0])
          }
          else if (args.length == 2) {
            binding.ant."$tag"(name: args[0], args[1])
          }
          else {
            builder.error("unexpected number of parameters for $tag")
          }
          if (tag == "module") {
            layoutInfo.usedModules << args[0].toString()
          }
        })
      }
      binding.setVariable("jar", {Object[] args ->
        if (args.length == 2) {
          def param0 = args[0]
          String name
          String duplicate = null
          if (param0 instanceof Map) {
            name = param0.name
            duplicate = param0.duplicate
          }
          else {
            name = (String)param0
          }
          if (duplicate == null) {
            duplicate = "fail"
          }
          binding.ant.jar(name: name, compress: builder.compressJars, duplicate: duplicate, args[1])
        }
        else {
          builder.error("unexpected number of parameters for 'jar' task: $args.length")
        }
      })

      def meta = new Expando()
      body.delegate = meta
      binding.ant.layout(toDir: dir, body)
      return layoutInfo
    })

    def contextLoaderRef = "GANT_CONTEXT_CLASS_LOADER"
    ClassLoader contextLoader = Thread.currentThread().contextClassLoader
    if (!(contextLoader instanceof AntClassLoader)) {
      contextLoader = new AntClassLoader(contextLoader, binding.ant.project, null)
    }
    binding.ant.project.addReference(contextLoaderRef, contextLoader)
    binding.ant.taskdef(name: "layout", loaderRef: contextLoaderRef, classname: "jetbrains.antlayout.tasks.LayoutTask")
  }

  private static JpsModule addModule(File imlFile, JpsModel model, JpsGantProjectBuilder builder) {
    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    def modules = JpsProjectLoader.loadModules(Collections.singletonList(imlFile.toPath()), JpsJavaSdkType.INSTANCE, pathVariables)
    def module = modules.get(0)
    model.project.addModule(module)
    builder.info("Module ${module.getName()} added to the project")
    return module
  }

  private void loadProject(String path, JpsModel model, JpsGantProjectBuilder builder) {
    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, path)
    if (builder.getDataStorageRoot() == null) {
      builder.setDataStorageRoot(Utils.getDataStorageRoot(path))
    }
    builder.info("Loaded project " + path + ": " + model.getProject().getModules().size() + " modules, " + model.getProject().getLibraryCollection().getLibraries().size() + " libraries")
    builder.exportModuleOutputProperties()
  }

  private void createJavaSdk(JpsGlobal global, String name, String homePath, Closure initializer) {
    def sdk = JpsJavaExtensionService.getInstance().addJavaSdk(global, name, homePath)
    def meta = new Expando()
    meta.classpath = {String path ->
      sdk.addRoot(new File(path), JpsOrderRootType.COMPILED)
    }
    initializer.delegate = meta
    initializer.call()
  }

  static String guessHome(Script script) {
    File home = new File(script["gant.file"].substring("file:".length()))

    while (home != null) {
      if (home.isDirectory()) {
        if (new File(home, ".idea").exists()) return home.getCanonicalPath()
      }

      home = home.getParentFile()
    }

    return null
  }
}
