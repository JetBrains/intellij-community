// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerDataSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.java.LookupNameUsage;
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection;
import org.jetbrains.kotlin.build.GeneratedFile;
import org.jetbrains.kotlin.build.GeneratedJvmClass;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot;
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline;
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor;
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.Services;
import org.jetbrains.kotlin.incremental.*;
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker;
import org.jetbrains.kotlin.incremental.components.ImportTracker;
import org.jetbrains.kotlin.incremental.components.InlineConstTracker;
import org.jetbrains.kotlin.incremental.components.LookupTracker;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents;
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion;
import org.jetbrains.kotlin.progress.CompilationCanceledException;
import org.jetbrains.kotlin.progress.CompilationCanceledStatus;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.tools.build.bazel.jvmIncBuilder.impl.KotlinPluginsKt.configurePlugins;
import static org.jetbrains.jps.util.Iterators.*;
import static org.jetbrains.kotlin.cli.common.ExitCode.OK;
import static org.jetbrains.kotlin.cli.common.arguments.ParseCommandLineArgumentsKt.parseCommandLineArguments;
import static org.jetbrains.kotlin.cli.plugins.PluginsOptionsParserKt.processCompilerPluginOptions;


/** @noinspection IO_FILE_USAGE*/
public class KotlinCompilerRunner implements CompilerRunner {
  private final BuildContext myContext;
  private final StorageManager myStorageManager;
  @NotNull NodeSourcePathMapper myPathMapper;

  private LookupTrackerImpl lookupTracker;
  private InlineConstTrackerImpl inlineConstTracker;
  private EnumWhenTrackerImpl enumWhenTracker;
  private ImportTrackerImpl importTracker;
  private final @NotNull Map<@NotNull String, @NotNull String> myPluginIdToPluginClasspath = new HashMap<>();

  public KotlinCompilerRunner(BuildContext context, StorageManager storageManager)  {
    myContext = context;
    myStorageManager = storageManager;
    myPathMapper = context.getPathMapper();

    // classpath map for compiler plugins
    Map<CLFlags, List<String>> flags = context.getFlags();
    Iterator<String> pluginCp = CLFlags.PLUGIN_CLASSPATH.getValue(flags).iterator();
    for (String pluginId : CLFlags.PLUGIN_ID.getValue(flags)) {
      myPluginIdToPluginClasspath.put(pluginId, pluginCp.hasNext()? pluginCp.next() : "");
    }
  }

  @Override
  public String getName() {
    return "Kotlinc Runner";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return src.toString().endsWith(".kt");
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, Iterable<NodeSource> deletedSources, DiagnosticSink diagnostic, OutputSink out) {
    try {
      K2JVMCompilerArguments kotlinArgs = buildKotlinCompilerArguments(myContext, sources);
      KotlinIncrementalCacheImpl incCache = new KotlinIncrementalCacheImpl(myStorageManager, flat(deletedSources, sources));
      Services services = buildServices(kotlinArgs.getModuleName(), incCache);
      MessageCollector messageCollector = new KotlinMessageCollector(diagnostic, this);
      // todo: make sure if we really need to process generated outputs after the compilation and not "in place"
      List<GeneratedClass> generatedClasses = new ArrayList<>();
      AbstractCliPipeline<K2JVMCompilerArguments> pipeline = createPipeline(out, generatedFile -> {
        if (generatedFile instanceof GeneratedJvmClass jvmClass) {
          String jvmClassName = jvmClass.getOutputClass().getClassName().getInternalName();
          for (File sourceFile : jvmClass.getSourceFiles()) {
            generatedClasses.add(new GeneratedClass(jvmClassName, sourceFile));
          }
        }
      });

      boolean completedOk = false;
      try {
        org.jetbrains.kotlin.cli.common.ExitCode exitCode = pipeline.execute(kotlinArgs, services, messageCollector);
        completedOk = exitCode == OK;
        return completedOk? ExitCode.OK : ExitCode.ERROR;
      }
      finally {
        processTrackers(out, generatedClasses);
        if (!completedOk || diagnostic.hasErrors()) {
          String moduleEntryPath = incCache.getModuleEntryPath();
          if (moduleEntryPath != null) {
            // ensure the output contains last known good value
            byte[] lastGoodModuleData = incCache.getModuleMappingData();
            myStorageManager.getOutputBuilder().putEntry(moduleEntryPath, lastGoodModuleData);
          }
        }
      }
      
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Throwable e) {
      diagnostic.report(Message.create(this, e));
      return ExitCode.ERROR;
    }
  }

  private record GeneratedClass(String jvmClassName, File source) {}

  private void processTrackers(OutputSink out, List<GeneratedClass> generated) {
    ensureTrackersInitialized();
    processLookupTracker(lookupTracker, out);

    for (GeneratedClass outputClass : generated) {
      processInlineConstTracker(inlineConstTracker, outputClass, out);
      processBothEnumWhenAndImportTrackers(enumWhenTracker, importTracker, outputClass, out);
    }
  }


  private static void processInlineConstTracker(InlineConstTrackerImpl inlineConstTracker,
                                                GeneratedClass output,
                                                OutputSink callback) {
    Map<String, Collection<ConstantRef>> constMap = inlineConstTracker.getInlineConstMap();
    Collection<ConstantRef> constantRefs = constMap.get(output.source.getPath());
    if (constantRefs == null) return;

    List<CompilerDataSink.ConstantRef> cRefs = new ArrayList<>();
    for (ConstantRef cRef : constantRefs) {
      String constType = cRef.getConstType();
      String descriptor = switch (constType) {
        case "Byte" -> "B";
        case "Short" -> "S";
        case "Int" -> "I";
        case "Long" -> "J";
        case "Float" -> "F";
        case "Double" -> "D";
        case "Boolean" -> "Z";
        case "Char" -> "C";
        case "String" -> "Ljava/lang/String;";
        default -> null;
      };
      if (descriptor != null) {
        cRefs.add(CompilerDataSink.ConstantRef.create(cRef.getOwner(), cRef.getName(), descriptor));
      }
    }

    if (!cRefs.isEmpty()) {
      callback.registerConstantReferences(output.jvmClassName, cRefs);
    }
  }

  private static void processBothEnumWhenAndImportTrackers(EnumWhenTrackerImpl enumWhenTracker, ImportTrackerImpl importTracker, GeneratedClass output, OutputSink callback) {
    Map<String, Collection<String>> whenMap = enumWhenTracker.getWhenExpressionFilePathToEnumClassMap();
    Map<String, Collection<String>> importMap = importTracker.getFilePathToImportedFqNamesMap();

    Collection<String> enumFqNameClasses = whenMap.get(output.source.getPath());
    Collection<String> importedFqNames = importMap.get(output.source.getPath());

    if (enumFqNameClasses == null && importedFqNames == null) return;

    List<String> enumClassesWithStar = enumFqNameClasses != null ?
                                       ContainerUtil.map(enumFqNameClasses, name -> name + ".*") :
                                       new ArrayList<>();

    callback.registerImports(output.jvmClassName,
                             importedFqNames != null ? importedFqNames : new ArrayList<>(),
                             enumClassesWithStar);
  }

  private static void processLookupTracker(LookupTrackerImpl lookupTracker, OutputSink callback) {
    for (var entry : lookupTracker.getLookups().entrySet()) {
      String symbolOwner = entry.getKey().getScope().replace('.', '/');
      String symbolName = entry.getKey().getName();
      LookupNameUsage usage = new LookupNameUsage(symbolOwner, symbolName);

      for (String file : entry.getValue()) {
        callback.registerUsage(file, usage);
      }
    }
  }

  private Services buildServices(String moduleName, IncrementalCache cacheImpl) {
    Services.Builder builder = new Services.Builder();
    lookupTracker = new LookupTrackerImpl(LookupTracker.DO_NOTHING.INSTANCE);
    inlineConstTracker = new InlineConstTrackerImpl();
    enumWhenTracker = new EnumWhenTrackerImpl();
    importTracker = new ImportTrackerImpl();

    builder.register(LookupTracker.class, lookupTracker);
    builder.register(InlineConstTracker.class, inlineConstTracker);
    builder.register(EnumWhenTracker.class, enumWhenTracker);
    builder.register(ImportTracker.class, importTracker);
    builder.register(
      IncrementalCompilationComponents.class,
      new KotlinIncrementalCompilationComponents(moduleName, cacheImpl)
    );

    builder.register(CompilationCanceledStatus.class, new CompilationCanceledStatus() {
      @Override
      public void checkCanceled() {
        if (myContext.isCanceled()) {
          throw new CompilationCanceledException();
        }
      }
    });

    return builder.build();
  }

  private AbstractCliPipeline<K2JVMCompilerArguments> createPipeline(OutputSink out, Consumer<GeneratedFile> outputItemCollector) {
    return new BazelJvmCliPipeline(createCompilerConfigurationUpdater(out), createOutputConsumer(out, outputItemCollector));
  }

  private @NotNull Function1<? super @NotNull CompilerConfiguration, @NotNull Unit> createCompilerConfigurationUpdater(OutputSink out) {
    return configuration -> {
      OutputFileSystem outputFileSystem = new OutputFileSystem(new KotlinVirtualFileProvider(out));
      configuration.add(CLIConfigurationKeys.CONTENT_ROOTS, new VirtualJvmClasspathRoot(outputFileSystem.root, false, true));
      configurePlugins(myPluginIdToPluginClasspath, myContext.getBaseDir(), registeredPluginInfo -> {
        assert registeredPluginInfo.getCompilerPluginRegistrar() != null;
        configuration.add(CompilerPluginRegistrar.Companion.getCOMPILER_PLUGIN_REGISTRARS(), registeredPluginInfo.getCompilerPluginRegistrar());
        if (!registeredPluginInfo.getPluginOptions().isEmpty()) {
          processCompilerPluginOptions((CommandLineProcessor)registeredPluginInfo.getCompilerPluginRegistrar(), registeredPluginInfo.getPluginOptions(), configuration);
        }
        return Unit.INSTANCE;
      });

      return Unit.INSTANCE;
    };
  }

  private @NotNull Function1<? super @NotNull OutputFileCollection, @NotNull Unit> createOutputConsumer(OutputSink outputSink, Consumer<GeneratedFile> clsCollector) {
    return outputCollection -> {
      outputCollection.asList().iterator().forEachRemaining(
        generatedOutput -> {
          String relativePath = generatedOutput.getRelativePath().replace(File.separatorChar, '/');
          GeneratedFile file;
          byte[] outputByteArray = generatedOutput.asByteArray();

          if (relativePath.endsWith(".class")) {
            file = new KotlinJvmGeneratedFile(
              generatedOutput.getSourceFiles(),
              new File(relativePath),
              outputByteArray,
              MetadataVersion.INSTANCE
            );
          }
          else {
            file = new GeneratedFile(
              generatedOutput.getSourceFiles(),
              new File(relativePath)
            );
          }
          clsCollector.accept(file);

          OutputSink.OutputFile.Kind kind =
            relativePath.endsWith(".class")? OutputSink.OutputFile.Kind.bytecode : OutputSink.OutputFile.Kind.other;

          outputSink.addFile(
            new OutputFileImpl(relativePath, kind, outputByteArray, false), map(generatedOutput.getSourceFiles(), myPathMapper::toNodeSource)
          );
        }
      );

      return Unit.INSTANCE;
    };
  }

  private static K2JVMCompilerArguments buildKotlinCompilerArguments(BuildContext context, Iterable<NodeSource> sources) {
    // todo: hash compiler configuration
    K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
    parseCommandLineArguments(context.getBuilderOptions().getKotlinOptions(), arguments, true);
    
    // additional setup directly from flags
    // todo: find corresponding cli option for every setting if possible
    Map<CLFlags, List<String>> flags = context.getFlags();
    arguments.setSkipPrereleaseCheck(true);
    arguments.setAllowUnstableDependencies(true);
    arguments.setDisableStandardScript(true);
    arguments.setAllowKotlinPackage(CLFlags.ALLOW_KOTLIN_PACKAGE.isFlagSet(flags));
    arguments.setWhenGuards(CLFlags.WHEN_GUARDS.isFlagSet(flags));
    arguments.setLambdas(CLFlags.LAMBDAS.getOptionalScalarValue(flags));
    arguments.setJvmDefault(CLFlags.JVM_DEFAULT.getOptionalScalarValue(flags));
    arguments.setInlineClasses(CLFlags.INLINE_CLASSES.isFlagSet(flags));
    arguments.setContextReceivers(CLFlags.CONTEXT_RECEIVERS.isFlagSet(flags));
    Iterable<String> friends = CLFlags.FRIENDS.getValue(flags);
    if (!isEmpty(friends)) {
      arguments.setFriendPaths(ensureCollection(map(friends, p -> context.getBaseDir().resolve(p).normalize().toString())).toArray(String[]::new));
    }
    NodeSourcePathMapper pathMapper = context.getPathMapper();
    arguments.setFreeArgs(collect(map(sources, ns -> pathMapper.toPath(ns).toString()), new ArrayList<>()));
    return arguments;
  }

  private static <T> Collection<T> ensureCollection(Iterable<T> seq) {
    return seq instanceof Collection<T>? (Collection<T>)seq : collect(seq, new ArrayList<>());
  }

  private void ensureTrackersInitialized() {
    List<String> nullTrackers = new ArrayList<>();
    if (lookupTracker == null) nullTrackers.add("lookup tracker");
    if (inlineConstTracker == null) nullTrackers.add("inline const tracker");
    if (enumWhenTracker == null) nullTrackers.add("enum-when tracker");
    if (importTracker == null) nullTrackers.add("import tracker");

    if (!nullTrackers.isEmpty()) {
      throw new IllegalStateException(
        "Following trackers are not initialized: " + String.join(", ", nullTrackers) +
        ". Make sure buildServices() is called before accessing trackers");
    }
  }
}
