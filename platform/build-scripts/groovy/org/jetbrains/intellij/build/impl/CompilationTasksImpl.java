// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.JavaVersion;
import groovy.lang.Closure;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.BuildOptions;
import org.jetbrains.intellij.build.CompilationContext;
import org.jetbrains.intellij.build.CompilationTasks;
import org.jetbrains.intellij.build.impl.compilation.CompilationPartsUtil;
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache;
import org.jetbrains.jps.model.module.JpsModule;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class CompilationTasksImpl implements CompilationTasks {
  public CompilationTasksImpl(CompilationContext context) {
    this.context = context;
    this.jpsCache = new PortableCompilationCache(context);
  }

  @Override
  public void compileModules(@Nullable Collection<String> moduleNames, @Nullable List<String> includingTestsInModules) {
    reuseCompiledClassesIfProvided();
    if (context.getOptions().getUseCompiledClassesFromProjectOutput()) {
      context.getMessages().info(
        "Compilation skipped, the compiled classes from \'" + String.valueOf(context.getProjectOutputDirectory()) + "\' will be used");
      resolveProjectDependencies();
    }
    else if (jpsCache.getCanBeUsed()) {
      context.getMessages().info("JPS remote cache will be used");
      jpsCache.downloadCacheAndCompileProject();
    }
    else if (context.getOptions().getPathToCompiledClassesArchive() != null) {
      context.getMessages().info(
        "Compilation skipped, the compiled classes from \'" + context.getOptions().getPathToCompiledClassesArchive() + "\' will be used");
      resolveProjectDependencies();
    }
    else if (context.getOptions().getPathToCompiledClassesArchivesMetadata() != null) {
      context.getMessages().info("Compilation skipped, the compiled classes from \'" +
                                 context.getOptions().getPathToCompiledClassesArchivesMetadata() +
                                 "\' will be used");
      resolveProjectDependencies();
    }
    else {
      if (!JavaVersion.current().isAtLeast(11)) {
        context.getMessages().error(
          "Build script must be executed under Java 11 to compile intellij project, but it\'s executed under Java " +
          String.valueOf(JavaVersion.current()));
      }


      resolveProjectDependencies();
      context.getMessages().progress("Compiling project");
      JpsCompilationRunner runner = new JpsCompilationRunner(context);
      try {
        if (moduleNames == null) {
          if (includingTestsInModules == null) {
            runner.buildAll();
          }
          else {
            runner.buildProduction();
          }
        }
        else {
          List<String> invalidModules = moduleNames.stream().filter(new Closure<Boolean>(this, this) {
            public Boolean doCall(String it) { return context.findModule(it) == null; }

            public Boolean doCall() {
              return doCall(null);
            }
          }).collect((Collector<? super String, ?, List<String>>)Collectors.toUnmodifiableList());
          if (!invalidModules.isEmpty()) {
            context.getMessages().warning("The following modules won\'t be compiled: " + String.valueOf(invalidModules));
          }

          runner.buildModules(DefaultGroovyMethods.findAll(DefaultGroovyMethods.collect(moduleNames, new Closure<JpsModule>(this, this) {
            public JpsModule doCall(String it) { return context.findModule(it); }

            public JpsModule doCall() {
              return doCall(null);
            }
          }), new Closure<Boolean>(this, this) {
            public Boolean doCall(JpsModule it) { return it != null; }

            public Boolean doCall() {
              return doCall(null);
            }
          }));
        }


        if (includingTestsInModules != null) {
          for (String moduleName : includingTestsInModules) {
            runner.buildModuleTests(context.findModule(moduleName));
          }
        }
      }
      catch (Throwable e) {
        context.getMessages().error("Compilation failed with exception: " + String.valueOf(e), e);
      }
    }
  }

  @Override
  public void buildProjectArtifacts(Set<String> artifactNames) {
    if (artifactNames.isEmpty()) {
      return;
    }


    try {
      boolean buildIncludedModules = !areCompiledClassesProvided(context.getOptions());
      if (buildIncludedModules && jpsCache.getCanBeUsed()) {
        jpsCache.downloadCacheAndCompileProject();
        buildIncludedModules = false;
      }

      new JpsCompilationRunner(context).buildArtifacts(artifactNames, buildIncludedModules);
    }
    catch (Throwable e) {
      context.getMessages().error("Building project artifacts failed with exception: " + String.valueOf(e), e);
    }
  }

  @Override
  public void resolveProjectDependencies() {
    new JpsCompilationRunner(context).resolveProjectDependencies();
  }

  @Override
  public void compileAllModulesAndTests() {
    compileModules(null, null);
  }

  @Override
  public void resolveProjectDependenciesAndCompileAll() {
    resolveProjectDependencies();
    context.getCompilationData().setStatisticsReported(false);
    compileAllModulesAndTests();
  }

  public static boolean areCompiledClassesProvided(BuildOptions options) {
    return options.getUseCompiledClassesFromProjectOutput() ||
           options.getPathToCompiledClassesArchive() != null ||
           options.getPathToCompiledClassesArchivesMetadata() != null;
  }

  @Override
  public void reuseCompiledClassesIfProvided() {
    synchronized (org.jetbrains.intellij.build.impl.CompilationTasksImpl) {
      if (context.getCompilationData().getCompiledClassesAreLoaded()) {
        return;
      }

      if (context.getOptions().getCleanOutputFolder()) {
        cleanOutput();
      }
      else {
        context.getMessages().info("cleanOutput step was skipped");
      }

      if (context.getOptions().getUseCompiledClassesFromProjectOutput()) {
        context.getMessages().info("Compiled classes reused from \'" + String.valueOf(context.getProjectOutputDirectory()) + "\'");
      }
      else if (context.getOptions().getPathToCompiledClassesArchivesMetadata() != null) {
        CompilationPartsUtil.fetchAndUnpackCompiledClasses(context.getMessages(), context.getProjectOutputDirectory(),
                                                           context.getOptions());
      }
      else if (context.getOptions().getPathToCompiledClassesArchive() != null) {
        unpackCompiledClasses(context.getProjectOutputDirectory().toPath());
      }
      else if (jpsCache.getCanBeUsed() && !jpsCache.isCompilationRequired()) {
        jpsCache.downloadCacheAndCompileProject();
      }

      context.getCompilationData().setCompiledClassesAreLoaded(true);
    }
  }

  private void unpackCompiledClasses(final Path classesOutput) {
    context.getMessages().block("Unpack compiled classes archive", new Supplier<Void>() {
      @Override
      public Void get() {
        NioFiles.deleteRecursively(classesOutput);
        new Decompressor.Zip(Path.of(context.getOptions().getPathToCompiledClassesArchive())).extract(classesOutput);
        return null;
      }
    });
  }

  private void cleanOutput() {
    final Set<String> outputDirectoriesToKeep = new HashSet<String>(5);
    outputDirectoriesToKeep.add("log");
    if (areCompiledClassesProvided(context.getOptions())) {
      outputDirectoriesToKeep.add("classes");
    }

    if (context.getOptions().getIncrementalCompilation()) {
      outputDirectoriesToKeep.add(context.getCompilationData().getDataStorageRoot().getName());
      outputDirectoriesToKeep.add("classes");
      outputDirectoriesToKeep.add("project-artifacts");
    }

    final Path outputPath = context.getPaths().getBuildOutputDir();
    context.getMessages().block(TracerManager.spanBuilder("clean output").setAttribute("path", outputPath.toString())
                                  .setAttribute(AttributeKey.stringArrayKey("outputDirectoriesToKeep"),
                                                List.copyOf(outputDirectoriesToKeep)), new Supplier<Void>() {
      @Override
      public Void get() {
        DirectoryStream<Path> dirStream = Files.newDirectoryStream(outputPath);
        try {
          Span span = Span.current();
          for (Path file : dirStream) {
            Attributes attributes = Attributes.of(AttributeKey.stringKey("dir"), outputPath.relativize(file).toString());
            if (outputDirectoriesToKeep.contains(file.getFileName().toString())) {
              span.addEvent("skip cleaning", attributes);
            }
            else {
              span.addEvent("delete", attributes);
              NioFiles.deleteRecursively(file);
            }
          }
        }
        finally {
          dirStream.close();
        }

        return null;
      }
    });
  }

  private final CompilationContext context;
  private final PortableCompilationCache jpsCache;
}
