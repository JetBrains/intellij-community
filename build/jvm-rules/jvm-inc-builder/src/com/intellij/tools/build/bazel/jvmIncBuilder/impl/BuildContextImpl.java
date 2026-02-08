// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.BuildProcessLogger;
import com.intellij.tools.build.bazel.jvmIncBuilder.BuilderOptions;
import com.intellij.tools.build.bazel.jvmIncBuilder.CLFlags;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.Message;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.ResourceGroup;
import com.intellij.tools.build.bazel.jvmIncBuilder.VMFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.bazel.jvm.Input;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
import org.jetbrains.jps.util.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jetbrains.jps.util.Iterators.asIterable;
import static org.jetbrains.jps.util.Iterators.find;
import static org.jetbrains.jps.util.Iterators.flat;
import static org.jetbrains.jps.util.Iterators.map;
import static org.jetbrains.jps.util.Iterators.unique;

/** @noinspection IO_FILE_USAGE*/
public class BuildContextImpl implements BuildContext {
  private static final Logger LOG = Logger.getLogger("com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildContextImpl");
  private static final List<String> ourExpectedUntrackedInputSuffixes = List.of(
    "/jvm-inc-builder/jvm-inc-builder_deploy.jar",
    "/rules/impl/MemoryLauncher.java"
  );
  private final String myTargetName;
  private final Map<CLFlags, List<String>> myFlags;
  private final long myUntrackedInputsDigest;
  private final List<String> myUnexpectedInputs = new ArrayList<>();
  
  private final boolean myAllowWarnings;
  private final Path myBaseDir;
  private final PathSourceMapper myPathMapper;
  private final Appendable myMessageSink;
  private final @NotNull Path myOutJar;
  private final @Nullable Path myAbiJar;
  private final @Nullable Path myKotlinCriStoragePath;
  private final Path myDataDir;

  private final @NotNull NodeSourceSnapshot mySources;
  private final @NotNull NodeSourceSnapshot myLibraries;
  private final @NotNull Iterable<ResourceGroup> myResources;
  private final boolean myIsRebuild;
  private final BuilderOptions myBuilderOptions;

  private final List<Message> myErrors = new ArrayList<>();
  private BuildProcessLogger myBuildProcessLogger;

  public BuildContextImpl(Path baseDir, Iterable<Input> inputs, Map<CLFlags, List<String>> flags, Appendable messageSink) {
    myFlags = Map.copyOf(flags);
    myTargetName = CLFlags.TARGET_LABEL.getMandatoryScalarValue(flags);
    myAllowWarnings = !"off".equals(CLFlags.WARN.getOptionalScalarValue(flags));
    myBaseDir = baseDir;
    myPathMapper = new PathSourceMapper(
      relPath -> {
        Path abs = baseDir.resolve(Path.of(relPath)).normalize();
        return abs.toString().replace(baseDir.getFileSystem().getSeparator(), "/");
      },
      absPath -> {
        Path relative = baseDir.relativize(Path.of(absPath)).normalize();
        return relative.toString().replace(baseDir.getFileSystem().getSeparator(), "/");
      }
    );
    myMessageSink = messageSink;
    myOutJar = baseDir.resolve(CLFlags.OUT.getMandatoryScalarValue(flags)).normalize();

    String abiPath = CLFlags.ABI_OUT.getOptionalScalarValue(flags);
    myAbiJar = abiPath != null? baseDir.resolve(abiPath).normalize() : null;

    String kotlinCriStoragePath = CLFlags.KOTLIN_CRI_OUT.getOptionalScalarValue(flags);
    myKotlinCriStoragePath = kotlinCriStoragePath != null ? baseDir.resolve(kotlinCriStoragePath).normalize() : null;

    myDataDir = myOutJar.resolveSibling(truncateExtension(myOutJar.getFileName().toString()) + DataPaths.DATA_DIR_NAME_SUFFIX);

    myIsRebuild = CLFlags.NON_INCREMENTAL.isFlagSet(flags);

    Map<String, byte[]> digestsMap = new HashMap<>();
    for (Input input : inputs) {
      digestsMap.put(input.path, input.digest);
    }

    List<Pair<String, byte[]>> untrackedInputs = new ArrayList<>();
    for (String path : unique(flat(map(CLFlags.PLUGIN_CLASSPATH.getValue(flags), cp -> cp.isBlank()? List.of() : asIterable(cp.split(":")))))) {
      byte[] digest = digestsMap.get(path);
      if (digest != null) {
        untrackedInputs.add(Pair.create(path, digest));
      }
      else {
        myUnexpectedInputs.add("!no-digest!: " + path);
      }
    }

    Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
    Function<String, String> getDigest = path -> base64.encodeToString(Objects.requireNonNull(digestsMap.remove(path)));

    Map<NodeSource, String> sourcesMap = new HashMap<>();
    for (String src : CLFlags.SRCS.getValue(flags)) {
      try {
        Path inputPath = baseDir.resolve(src).toRealPath(LinkOption.NOFOLLOW_LINKS); // ensure the input path names have exactly the same case as on the disk
        assert isSourceDependency(inputPath);
        sourcesMap.put(myPathMapper.toNodeSource(inputPath), getDigest.apply(src));
      }
      catch (IOException e) {
        report(Message.create(null, Message.Kind.ERROR, "Unable to resolve relative path " + src, e));
      }
    }
    mySources = new SourceSnapshotImpl(sourcesMap);

    Map<NodeSource, String> libsMap = new LinkedHashMap<>(); // for the classpath order is important
    for (String cpEntry : CLFlags.CP.getValue(flags)) {
      Path path = baseDir.resolve(cpEntry).normalize();
      libsMap.put(myPathMapper.toNodeSource(path), getDigest.apply(cpEntry));
    }
    myLibraries = new SourceSnapshotImpl(libsMap);

    List<ResourceGroup> resources = new ArrayList<>();
    for (String resourcesEntry : CLFlags.RESOURCES.getValue(flags)) {
      String[] parts = resourcesEntry.split(":", 3);
      String stripPrefix = parts[0];
      String addPrefix = parts[1];
      Map<NodeSource, String> resourcesMap = new HashMap<>();
      for (String file : parts[2].split(":")) {
        Path path = baseDir.resolve(file).normalize();
        String digest = getDigest.apply(file);
        resourcesMap.put(myPathMapper.toNodeSource(path), digest);
      }
      if (!resourcesMap.isEmpty()) {
        resources.add(new ResourceGroupImpl(resourcesMap, stripPrefix, addPrefix));
      }
    }
    myResources = resources;

    for (Iterator<Map.Entry<String, byte[]>> it = digestsMap.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, byte[]> entry = it.next();
      String input = entry.getKey();
      if (input.endsWith(DataPaths.PARAMS_FILE_NAME_SUFFIX)) {
        it.remove(); // params are tracked selectively by flags digest
      }
      else if (find(ourExpectedUntrackedInputSuffixes, input::endsWith) != null) {
        untrackedInputs.add(Pair.create(input, entry.getValue()));
      }
    }

    Collections.sort(untrackedInputs, Comparator.comparing(p -> p.first)); // ensure same order over invocations;
    myUntrackedInputsDigest = Utils.digestContent(map(untrackedInputs, p -> {
      digestsMap.remove(p.first);
      return p.second;
    }));

    for (Map.Entry<String, byte[]> entry : digestsMap.entrySet()) {
      myUnexpectedInputs.add(base64.encodeToString(entry.getValue()) + ": " + entry.getKey());
    }

    myBuilderOptions = BuilderOptions.create(buildJavaOptions(flags), buildKotlinOptions(flags, map(myLibraries.getElements(), myPathMapper::toPath)));
    myBuildProcessLogger = VMFlags.isBuildProcessLoggerEnabled()? new BuildProcessLoggerImpl(baseDir) : BuildProcessLogger.EMPTY;
  }

  private static @NotNull List<String> buildKotlinOptions(Map<CLFlags, List<String>> flags, @NotNull Iterable<@NotNull Path> classpath) {
    List<String> options = new ArrayList<>();
    options.add("-module-name");
    options.add(CLFlags.KOTLIN_MODULE_NAME.getMandatoryScalarValue(flags));

    if (KotlinCompilerConfig.ENABLE_INCREMENTAL_COMPILATION) {
      options.add("-Xenable-incremental-compilation");
    }
    if (!KotlinCompilerConfig.INCLUDE_STDLIB) {
      options.add("-no-stdlib");
    }
    if (!KotlinCompilerConfig.INCLUDE_REFLECTION) {
      options.add("-no-reflect");
    }

    String apiVersion = CLFlags.API_VERSION.getOptionalScalarValue(flags);
    if (apiVersion != null) {
      options.add("-api-version");
      options.add(apiVersion);
    }

    String langVersion = CLFlags.LANGUAGE_VERSION.getOptionalScalarValue(flags);
    if (langVersion != null) {
      options.add("-language-version");
      options.add(langVersion);
    }

    String jvmTarget = CLFlags.JVM_TARGET.getOptionalScalarValue(flags);
    if (jvmTarget != null) {
      options.add("-jvm-target");
      options.add("8".equals(jvmTarget)? "1.8" : jvmTarget);
    }

    StringBuilder optIns = new StringBuilder();
    for (String annotName : CLFlags.OPT_IN.getValue(flags)) {
      optIns.append(annotName).append(",");
    }
    if (!optIns.isEmpty()) {
      options.add("-opt-in=" + optIns.deleteCharAt(optIns.length() - 1));
    }

    String warn = CLFlags.WARN.getOptionalScalarValue(flags);
    if ("off".equals(warn)) {
      options.add("-nowarn");
    }
    else if ("error".equals(warn)) {
      options.add("-Werror");
    }
    else if (warn != null && !"report".equals(warn)) {
      throw new IllegalArgumentException("Unsupported kotlinc warning option: " + warn);
    }

    if (CLFlags.X_ALLOW_RESULT_RETURN_TYPE.isFlagSet(flags)) {
      options.add("-Xallow-result-return-type");
    }
    if (CLFlags.X_STRICT_JAVA_NULLABILITY_ASSERTIONS.isFlagSet(flags)) {
      options.add("-Xstrict-java-nullability-assertions");
    }
    if (CLFlags.X_WASM_ATTACH_JS_EXCEPTION.isFlagSet(flags)) {
      options.add("-Xwasm-attach-js-exception");
    }
    for (String flag : CLFlags.X_X_LANGUAGE.getValue(flags)) {
      options.add("-XXLanguage:" + flag);
    }

    StringBuilder cp = new StringBuilder();
    for (Path element : classpath) {
      if (!cp.isEmpty()) {
        cp.append(File.pathSeparator);
      }
      cp.append(element);
    }
    if (!cp.isEmpty()) {
      options.add("-classpath");
      options.add(cp.toString());
    }

    return options;
  }
  
  private @NotNull List<String> buildJavaOptions(Map<CLFlags, List<String>> flags) {
    // for now, only options available in the flags map can be specified in the build configuration
    List<String> options = new ArrayList<>();
    if (JavaCompilerConfig.GENERATE_DEBUG_INFO) {
      options.add("-g"); 
    }
    if (JavaCompilerConfig.REPORT_DEPRECATION) {
      options.add("-deprecation");
    }

    String warn = CLFlags.WARN.getOptionalScalarValue(flags);
    if ("off".equals(warn)) {
      options.add("-nowarn");
    }
    else if ("error".equals(warn)) {
      options.add("-Werror");
    }
    else if (warn != null && !"report".equals(warn)) {
      throw new IllegalArgumentException("Unsupported javac warning option: " + warn);
    }

    if (CLFlags.NO_PROC.isFlagSet(flags)) {
      options.add("-proc:none");
    }

    options.add("-encoding");
    options.add("UTF-8");

    String jvmTarget = CLFlags.JVM_TARGET.getOptionalScalarValue(flags);
    if (jvmTarget != null) {
      if (shouldUseReleaseOption(jvmTarget)) {
        options.add("--release");
        options.add(jvmTarget);
      }
      else {
        options.add("-source");
        options.add(jvmTarget);

        options.add("-target");
        options.add(jvmTarget);
        
        // todo: support '-system' option to specify the JDK against which the generated code should be linked
      }
    }

    Path trashDir = DataPaths.getTrashDir(this);
    options.add("-s");
    options.add(trashDir.toString()); // put AP-generated sources to trash dir

    for (String exp : CLFlags.ADD_EXPORT.getValue(flags)) {
      options.add("--add-exports");
      options.add(exp);
    }
    for (String exp : CLFlags.ADD_READS.getValue(flags)) {
      options.add("--add-reads");
      options.add(exp);
    }
    return options;
  }

  private static boolean shouldUseReleaseOption(String jvmTarget) {
    if (!JavaCompilerConfig.USE_RELEASE_OPTION) {
      return false;
    }
    // todo: if worker's compatibility with jvm versions <= 10 is required, parse Properties.getProperty("java.version")
    int compilerVersion = Runtime.version().feature();
    int targetPlatformVersion = parseTargetPlatformVersion(jvmTarget);
    // --release option is supported in java9+ and higher
    if (compilerVersion >= 9 && targetPlatformVersion > 0) {
      // Only specify '--release' when cross-compilation is indeed really required.
      // Otherwise, '--release' may not be compatible with other compilation options, e.g. exporting a package from system module
      return compilerVersion != targetPlatformVersion;
    }
    return false;
  }

  private static int parseTargetPlatformVersion(String target) {
    if (target != null) {
      target = target.trim();
      int dotIndex = target.lastIndexOf(".");
      try {
        return Integer.parseInt(dotIndex < 0? target : target.substring(dotIndex + 1));
      }
      catch (NumberFormatException e) {
        LOG.log(Level.INFO, "Error parsing JVM target version ", e);
      }
    }
    return -1;
  }

  private static boolean isSourceDependency(Path path) {
    return path != null && RunnerRegistry.isCompilableSource(path);
  }

  @Override
  public String getTargetName() {
    return myTargetName;
  }

  @Override
  public boolean isRebuild() {
    return myIsRebuild;
  }

  @Override
  public boolean isCanceled() {
    return false; // todo
  }

  @Override
  public Map<CLFlags, List<String>> getFlags() {
    return myFlags;
  }

  @Override
  public long getUntrackedInputsDigest() {
    return myUntrackedInputsDigest;
  }

  @Override
  public @NotNull Path getBaseDir() {
    return myBaseDir;
  }

  @Override
  public @NotNull Path getDataDir() {
    return myDataDir;
  }

  @Override
  public @NotNull Path getOutputZip() {
    return myOutJar;
  }

  @Override
  public @Nullable Path getAbiOutputZip() {
    return myAbiJar;
  }

  @Override
  public @Nullable Path getKotlinCriStoragePath() {
    return myKotlinCriStoragePath;
  }

  @Override
  public @NotNull NodeSourceSnapshot getSources() {
    return mySources;
  }

  @Override
  public NodeSourceSnapshot getBinaryDependencies() {
    return myLibraries;
  }

  @Override
  public Iterable<ResourceGroup> getResources() {
    return myResources;
  }

  @Override
  public Iterable<String> getUnexpectedInputs() {
    return myUnexpectedInputs;
  }

  @Override
  public BuilderOptions getBuilderOptions() {
    return myBuilderOptions;
  }

  @Override
  public NodeSourcePathMapper getPathMapper() {
    return myPathMapper;
  }

  @Override
  public BuildProcessLogger getBuildLogger() {
    return myBuildProcessLogger; // used for tests
  }

  @Override
  public void report(Message msg) {
    try {
      if (msg.getKind() == Message.Kind.ERROR) {
        myErrors.add(msg);
      }
      
      if (!myAllowWarnings) {

        if (msg.getKind() == Message.Kind.WARNING) {
          return;
        }

        // Some warnings in javac are impossible to disable
        // They're also reported as notes, not warnings
        // It greatly pollutes compilation output
        String text = msg.getText();
        if (text.startsWith("Some input files use unchecked or unsafe operations.") ||
            text.startsWith("Some input files use or override a deprecated API that is marked for removal.") ||
            text.startsWith("Some input files additionally use or override a deprecated API.") ||
            text.startsWith("Recompile with -Xlint:unchecked for details.") ||
            text.startsWith("Recompile with -Xlint:removal for details.") ||
            text.contains("uses or overrides a deprecated API that is marked for removal") ||
            text.contains("uses unchecked or unsafe operations"))  {
          return;
        }
      }

      if (msg.getSource() != null) {
        myMessageSink.append(msg.getSource().getName()).append(": ");
      }
      if (msg.getKind() == Message.Kind.ERROR) {
        myMessageSink.append("Error: ");
      }
      myMessageSink.append(msg.getText()).append("\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean hasErrors() {
    return !myErrors.isEmpty();
  }

  @Override
  public Iterable<Message> getErrors() {
    return myErrors;
  }

  private static String truncateExtension(String filename) {
    return DataPaths.truncateExtension(filename);  // todo: inline the method
  }
}
