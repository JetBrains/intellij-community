// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.compilation.CompilationPartsUtil
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache
import org.jetbrains.jps.model.java.JdkVersionDetector

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import java.util.stream.Collectors

@CompileStatic
final class CompilationTasksImpl extends CompilationTasks {
  private final CompilationContext context
  private final PortableCompilationCache jpsCache

  CompilationTasksImpl(CompilationContext context) {
    this.context = context
    this.jpsCache = new PortableCompilationCache(context)
  }

  @Override
  void compileModules(Collection<String> moduleNames, List<String> includingTestsInModules) {
    reuseCompiledClassesIfProvided()
    if (context.options.useCompiledClassesFromProjectOutput) {
      context.messages.info("Compilation skipped, the compiled classes from '${context.projectOutputDirectory}' will be used")
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
      resolveProjectDependencies()
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
          List<String> invalidModules = moduleNames.stream()
            .filter { context.findModule(it) == null }
            .collect(Collectors.toUnmodifiableList())
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
      try {
        def buildIncludedModules = !areCompiledClassesProvided(context.options)
        if (buildIncludedModules && jpsCache.canBeUsed) {
          jpsCache.downloadCacheAndCompileProject()
          buildIncludedModules = false
        }
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

  static boolean areCompiledClassesProvided(BuildOptions options) {
    return options.useCompiledClassesFromProjectOutput ||
           options.pathToCompiledClassesArchive != null ||
           options.pathToCompiledClassesArchivesMetadata != null
  }

  @Override
  void reuseCompiledClassesIfProvided() {
    synchronized (CompilationTasksImpl) {
      if (context.compilationData.compiledClassesAreLoaded) {
        return
      }
      if (context.options.cleanOutputFolder) {
        cleanOutput()
      }
      else {
        context.messages.info("cleanOutput step was skipped")
      }
      if (context.options.useCompiledClassesFromProjectOutput) {
        context.messages.info("Compiled classes reused from '${context.projectOutputDirectory}'")
      }
      else if (context.options.pathToCompiledClassesArchivesMetadata != null) {
        CompilationPartsUtil.fetchAndUnpackCompiledClasses(context.messages, context.projectOutputDirectory, context.options)
      }
      else if (context.options.pathToCompiledClassesArchive != null) {
        unpackCompiledClasses(context.projectOutputDirectory.toPath())
      }
      else if (jpsCache.canBeUsed && !jpsCache.isCompilationRequired()) {
        jpsCache.downloadCacheAndCompileProject()
      }
      context.compilationData.compiledClassesAreLoaded = true
    }
  }

  private void unpackCompiledClasses(Path classesOutput) {
    context.messages.block("Unpack compiled classes archive", new Supplier<Void>() {
      @Override
      Void get() {
        NioFiles.deleteRecursively(classesOutput)
        new Decompressor.Zip(Path.of(context.options.pathToCompiledClassesArchive)).extract(classesOutput)
        return null
      }
    })
  }

  private void cleanOutput() {
    List<String> outputDirectoriesToKeep = ["log"]
    if (areCompiledClassesProvided(context.options)) {
      outputDirectoriesToKeep.add("classes")
    }
    if (context.options.incrementalCompilation) {
      outputDirectoriesToKeep.add(context.compilationData.dataStorageRoot.name)
      outputDirectoriesToKeep.add("classes")
      outputDirectoriesToKeep.add("project-artifacts")
    }

    BuildMessages messages = context.messages
    Path outputPath = Path.of(context.paths.buildOutputRoot)
    messages.block("Clean output", new Supplier<Void>() {
      @Override
      Void get() {
        messages.progress("Cleaning output directory $outputPath")
        DirectoryStream<Path> dirStream = Files.newDirectoryStream(outputPath)
        try {
          for (Path file : dirStream) {
            if (outputDirectoriesToKeep.contains(file.fileName.toString())) {
              messages.info("Skipped cleaning for $file")
            }
            else {
              messages.info("Deleting $file")
              NioFiles.deleteRecursively(file)
            }
          }
        }
        finally {
          dirStream.close()
        }
        return null
      }
    })
  }
}
