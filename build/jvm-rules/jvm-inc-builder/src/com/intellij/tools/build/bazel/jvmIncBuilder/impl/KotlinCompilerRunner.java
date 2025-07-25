// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerDataSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputOrigin;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.java.LookupNameUsage;
import org.jetbrains.kotlin.backend.common.output.OutputFile;
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection;
import org.jetbrains.kotlin.build.GeneratedFile;
import org.jetbrains.kotlin.build.GeneratedJvmClass;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot;
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline;
import org.jetbrains.kotlin.compiler.plugin.CliOptionValue;
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
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
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
  private final List<String> myJavaSources;

  private final @Nullable String myModuleEntryPath;
  private byte @Nullable [] myLastGoodModuleEntryContent;

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

    myJavaSources = collect(
      map(filter(context.getSources().getElements(), KotlinCompilerRunner::isJavaSource), ns -> myPathMapper.toPath(ns).toString()), new ArrayList<>()
    );

    String moduleEntryPath = null;
    try {
      ZipOutputBuilderImpl outBuilder = storageManager.getOutputBuilder();
      moduleEntryPath = find(outBuilder.listEntries("META-INF/"), n -> n.endsWith(DataPaths.KOTLIN_MODULE_EXTENSION));
      if (moduleEntryPath != null) {
        myLastGoodModuleEntryContent = outBuilder.getContent(moduleEntryPath);
      }
    }
    catch (IOException e) {
      context.report(Message.create(this, e));
    }
    myModuleEntryPath = moduleEntryPath;
  }

  @Override
  public String getName() {
    return "Kotlinc Runner";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return isKotlinSource(src);
  }

  private static boolean isKotlinSource(NodeSource src) {
    return src.toString().endsWith(".kt");
  }
  
  private static boolean isJavaSource(NodeSource src) {
    return src.toString().endsWith(".java");
  }

  @Override
  public Iterable<String> getOutputPathsToDelete() {
    return myModuleEntryPath != null? List.of(myModuleEntryPath) : List.of();
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, Iterable<NodeSource> deletedSources, DiagnosticSink diagnostic, OutputSink out) throws Exception {
    try {
      if (isEmpty(sources)) {
        return ExitCode.OK;
      }
      K2JVMCompilerArguments kotlinArgs = buildKotlinCompilerArguments(myContext, sources);
      KotlinIncrementalCacheImpl incCache = new KotlinIncrementalCacheImpl(myStorageManager, flat(deletedSources, sources), myModuleEntryPath, myLastGoodModuleEntryContent);
      OutputVirtualFile outputFileSystemRoot = new OutputFileSystem(new KotlinVirtualFileProvider(out)).root;
      Services services = buildServices(kotlinArgs.getModuleName(), incCache, outputFileSystemRoot);
      MessageCollector messageCollector = new KotlinMessageCollector(diagnostic, this);
      // todo: make sure if we really need to process generated outputs after the compilation and not "in place"
      List<GeneratedClass> generatedClasses = new ArrayList<>();
      AbstractCliPipeline<K2JVMCompilerArguments> pipeline = createPipeline(out, outputFileSystemRoot, generatedFile -> {
        String jvmClassName = null;
        if (generatedFile instanceof KotlinJvmGeneratedFile jvmClass) {
          jvmClassName = jvmClass.getOutputClass().getClassName().getInternalName();
        }
        else if (generatedFile instanceof GeneratedJvmClass jvmClass) {
          jvmClassName = jvmClass.getOutputClass().getClassName().getInternalName();
        }
        if (jvmClassName != null) {
          for (File sourceFile : generatedFile.getSourceFiles()) {
            generatedClasses.add(new GeneratedClass(jvmClassName, sourceFile));
          }
        }
      });

      boolean completedOk = false;
      try {
        logCompiledFiles(myContext, sources);

        org.jetbrains.kotlin.cli.common.ExitCode exitCode = pipeline.execute(kotlinArgs, services, messageCollector);

        // todo: provide this info under 'verbose' flag
        //if (messageCollector.hasErrors()) {
        //  diagnostic.report(Message.create(this, Message.Kind.INFO, "Compilation finished with errors. Compiler options used: " + myContext.getBuilderOptions().getKotlinOptions()));
        //}

        completedOk = exitCode == OK;
        return completedOk? ExitCode.OK : ExitCode.ERROR;
      }
      finally {
        processTrackers(out, generatedClasses);
        if (myModuleEntryPath != null && completedOk && !messageCollector.hasErrors()) {
          byte[] updated = myStorageManager.getOutputBuilder().getContent(myModuleEntryPath);
          if (updated == null) {
            // report probable error
            diagnostic.report(Message.info(this, "Module entry \"" + myModuleEntryPath +"\" has not been generated for target \"" + myContext.getTargetName() + "\""));
          }
          myLastGoodModuleEntryContent = updated; // save the updated state for the next round
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

  private void processLookupTracker(LookupTrackerImpl lookupTracker, OutputSink callback) {
    Map<String, NodeSource> pathMapperCache = new HashMap<>();
    for (var entry : lookupTracker.getLookups().entrySet()) {
      String symbolOwner = entry.getKey().getScope().replace('.', '/');
      String symbolName = entry.getKey().getName();
      LookupNameUsage usage = new LookupNameUsage(symbolOwner, symbolName);

      for (String file : entry.getValue()) {
        callback.registerUsage(pathMapperCache.computeIfAbsent(file, k -> myPathMapper.toNodeSource(k)), usage);
      }
    }
  }

  private Services buildServices(String moduleName, IncrementalCache cacheImpl, VirtualFile outputRoot) {
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
      new KotlinIncrementalCompilationComponents(moduleName, cacheImpl, outputRoot)
    );

    builder.register(CompilationCanceledStatus.class, new CancelStatusImpl(myContext));

    return builder.build();
  }

  private AbstractCliPipeline<K2JVMCompilerArguments> createPipeline(OutputSink out, VirtualFile outputRoot, Consumer<GeneratedFile> outputItemCollector) throws IOException {
    return new BazelJvmCliPipeline(createCompilerConfigurationUpdater(out, outputRoot), createOutputConsumer(out, outputItemCollector));
  }

  private @NotNull Function1<? super @NotNull CompilerConfiguration, @NotNull Unit> createCompilerConfigurationUpdater(OutputSink out, VirtualFile outputRoot) throws IOException {
    var abiConsumer = createAbiOutputConsumer(myStorageManager.getAbiOutputBuilder());
    return configuration -> {
      configuration.add(CLIConfigurationKeys.CONTENT_ROOTS, new VirtualJvmClasspathRoot(outputRoot, false, true));
      configurePlugins(myPluginIdToPluginClasspath, myContext.getBaseDir(), abiConsumer, out, myStorageManager, registeredPluginInfo -> {
        CompilerPluginRegistrar registrar = Objects.requireNonNull(registeredPluginInfo.getCompilerPluginRegistrar());
        configuration.add(CompilerPluginRegistrar.Companion.getCOMPILER_PLUGIN_REGISTRARS(), registrar);
        List<CliOptionValue> pluginOptions = registeredPluginInfo.getPluginOptions();
        if (!pluginOptions.isEmpty()) {
          CommandLineProcessor clProcessor = Objects.requireNonNull(registeredPluginInfo.getCommandLineProcessor());
          processCompilerPluginOptions(clProcessor, pluginOptions, configuration);
        }
        return Unit.INSTANCE;
      });

      return Unit.INSTANCE;
    };
  }

  private @NotNull Function1<? super @NotNull OutputFileCollection, @NotNull Unit> createOutputConsumer(OutputSink outputSink, Consumer<GeneratedFile> clsCollector) {
    return outputCollection -> {
      for (OutputFile generatedOutput : outputCollection.asList()) {
        String relativePath = generatedOutput.getRelativePath().replace(File.separatorChar, '/');
        byte[] outputByteArray = generatedOutput.asByteArray();

        com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile.Kind kind;
        GeneratedFile file;
        if (relativePath.endsWith(".class")) {
          kind = com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile.Kind.bytecode;
          file = new KotlinJvmGeneratedFile(generatedOutput.getSourceFiles(), new File(relativePath), outputByteArray, MetadataVersion.INSTANCE);
        }
        else {
          kind = com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputFile.Kind.other;
          file = new GeneratedFile(generatedOutput.getSourceFiles(), new File(relativePath));
        }
        clsCollector.accept(file);

        outputSink.addFile(
          new OutputFileImpl(relativePath, kind, outputByteArray, false),
          OutputOrigin.create(OutputOrigin.Kind.kotlin, collect(map(generatedOutput.getSourceFiles(), myPathMapper::toNodeSource), new ArrayList<>()))
        );
      }

      return Unit.INSTANCE;
    };
  }

  private static @Nullable Function1<? super @NotNull OutputFileCollection, @NotNull Unit> createAbiOutputConsumer(@Nullable ZipOutputBuilder abiOutput) {
    return abiOutput == null? null : outputCollection -> {
      for (OutputFile generatedOutput : outputCollection.asList()) {
        String relativePath = generatedOutput.getRelativePath().replace(File.separatorChar, '/');
        abiOutput.putEntry(relativePath, generatedOutput.asByteArray());
      }
      return Unit.INSTANCE;
    };
  }

  private K2JVMCompilerArguments buildKotlinCompilerArguments(BuildContext context, Iterable<NodeSource> sources) {
    // todo: hash compiler configuration
    K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
    parseCommandLineArguments(context.getBuilderOptions().getKotlinOptions(), arguments, true);
    
    // additional setup directly from flags
    // todo: find corresponding cli option for every setting if possible
    Map<CLFlags, List<String>> flags = context.getFlags();
    arguments.setSkipPrereleaseCheck(CLFlags.X_SKIP_PRERELEASE_CHECK.isFlagSet(flags));
    arguments.setSkipMetadataVersionCheck(CLFlags.SKIP_METADATA_VERSION_CHECK.isFlagSet(flags));
    arguments.setAllowUnstableDependencies(CLFlags.X_ALLOW_UNSTABLE_DEPENDENCIES.isFlagSet(flags));
    arguments.setDisableStandardScript(true);
    if (arguments.getLanguageVersion() == null && arguments.getApiVersion() == null) {
      // defaults
      arguments.setApiVersion("2.2");     // todo: find a way to configure this in input parameters
      arguments.setLanguageVersion("2.2"); // todo: find a way to configure this in input parameters
    }
    else if (arguments.getLanguageVersion() == null) {
      arguments.setLanguageVersion(arguments.getApiVersion());
    }
    else if (arguments.getApiVersion() == null) {
      arguments.setApiVersion(arguments.getLanguageVersion());
    }
    String explicitApiMode = CLFlags.X_EXPLICIT_API_MODE.getOptionalScalarValue(flags);
    if (explicitApiMode != null) {
      arguments.setExplicitApi(explicitApiMode);
    }
    arguments.setAllowKotlinPackage(CLFlags.X_ALLOW_KOTLIN_PACKAGE.isFlagSet(flags));
    arguments.setWhenGuards(CLFlags.X_WHEN_GUARDS.isFlagSet(flags));
    arguments.setLambdas(CLFlags.X_LAMBDAS.getOptionalScalarValue(flags));
    arguments.setJvmDefault(CLFlags.X_JVM_DEFAULT.getOptionalScalarValue(flags));
    arguments.setInlineClasses(CLFlags.X_INLINE_CLASSES.isFlagSet(flags));
    arguments.setContextReceivers(CLFlags.X_CONTEXT_RECEIVERS.isFlagSet(flags));
    arguments.setContextParameters(CLFlags.X_CONTEXT_PARAMETERS.isFlagSet(flags));
    arguments.setNoCallAssertions(CLFlags.X_NO_CALL_ASSERTIONS.isFlagSet(flags));
    arguments.setNoParamAssertions(CLFlags.X_NO_PARAM_ASSERTIONS.isFlagSet(flags));
    arguments.setSamConversions(CLFlags.X_SAM_CONVERSIONS.getOptionalScalarValue(flags));
    arguments.setConsistentDataClassCopyVisibility(CLFlags.X_CONSISTENT_DATA_CLASS_COPY_VISIBILITY.isFlagSet(flags));
    Iterable<String> friends = CLFlags.FRIENDS.getValue(flags);
    if (!isEmpty(friends)) {
      arguments.setFriendPaths(ensureCollection(map(friends, p -> context.getBaseDir().resolve(p).normalize().toString())).toArray(String[]::new));
    }
    NodeSourcePathMapper pathMapper = context.getPathMapper();
    arguments.setFreeArgs(collect(flat(map(sources, ns -> pathMapper.toPath(ns).toString()), myJavaSources), new ArrayList<>()));
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

  private static class CancelStatusImpl implements CompilationCanceledStatus {
    private final Reference<BuildContext> myContextRef;

    CancelStatusImpl(BuildContext context) {
      myContextRef = new WeakReference<>(context);
    }

    @Override
    public void checkCanceled() {
      BuildContext ctx = myContextRef.get();
      if (ctx != null && ctx.isCanceled()) {
        throw new CompilationCanceledException();
      }
    }
  }
}
