// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.jetbrains.jps.util.Iterators.map;

/** @noinspection IO_FILE_USAGE*/
public class BuildContextImpl implements BuildContext {
  private final String myTargetName;
  private final Map<CLFlags, List<String>> myFlags;
  private final boolean myAllowWarnings;
  private final Path myBaseDir;
  private final PathSourceMapper myPathMapper;
  private final Appendable myMessageSink;
  private final @NotNull Path myOutJar;
  private final @Nullable Path myAbiJar;
  private final Path myDataDir;

  private final @NotNull NodeSourceSnapshot mySources;
  private final @NotNull NodeSourceSnapshot myLibraries;
  private final boolean myIsRebuild;
  private final BuilderOptions myBuilderOptions;

  private volatile boolean myHasErrors;

  public BuildContextImpl(Path baseDir, Iterable<String> inputs, Iterable<byte[]> inputDigests, Map<CLFlags, List<String>> flags, Appendable messageSink) {
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

    myDataDir = myOutJar.resolveSibling(truncateExtension(myOutJar.getFileName().toString()) + DataPaths.DATA_DIR_NAME_SUFFIX);
    
    myIsRebuild = CLFlags.NON_INCREMENTAL.isFlagSet(flags);

    Map<NodeSource, String> sourcesMap = new HashMap<>();
    Map<Path, String> otherInputsMap = new HashMap<>();
    Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
    Iterator<String> digestsIterator = map(inputDigests, base64::encodeToString).iterator();
    for (Path inputPath : map(inputs, input -> baseDir.resolve(input).normalize())) {
      String inputDigest = digestsIterator.hasNext()? digestsIterator.next() : "";
      if (isSourceDependency(inputPath)) {
        sourcesMap.put(myPathMapper.toNodeSource(inputPath), inputDigest);
      }
      else {
        otherInputsMap.put(inputPath, inputDigest);
      }
    }
    mySources = new SourceSnapshotImpl(sourcesMap);

    Map<NodeSource, String> libsMap = new LinkedHashMap<>(); // for the classpath order is important
    for (String cpEntry : CLFlags.CP.getValue(flags)) {
      Path path = baseDir.resolve(cpEntry).normalize();
      libsMap.put(myPathMapper.toNodeSource(path), otherInputsMap.getOrDefault(path, ""));
    }
    myLibraries = new SourceSnapshotImpl(libsMap);

    myBuilderOptions = BuilderOptions.create(buildJavaOptions(flags), buildKotlinOptions(flags, map(myLibraries.getElements(), myPathMapper::toPath)));
  }

  private static @NotNull List<String> buildKotlinOptions(Map<CLFlags, List<String>> flags, @NotNull Iterable<@NotNull Path> classpath) {
    List<String> options = new ArrayList<>();
    options.add("-module-name");
    options.add(CLFlags.KOTLIN_MODULE_NAME.getMandatoryScalarValue(flags));

    options.add("-no-stdlib");
    options.add("-no-reflect");

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
    else if (warn != null) {
      throw new IllegalArgumentException("unsupported kotlinc warning option: " + warn);
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
      options.add("-werror");
    }
    else if (warn != null) {
      throw new IllegalArgumentException("unsupported javac warning option: " + warn);
    }
    
    options.add("-encoding");
    options.add("UTF-8");

    String jvmTarget = CLFlags.JVM_TARGET.getOptionalScalarValue(flags);
    if (jvmTarget != null) {
      if (JavaCompilerConfig.USE_RELEASE_OPTION) {
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
  public @NotNull NodeSourceSnapshot getSources() {
    return mySources;
  }

  @Override
  public NodeSourceSnapshot getBinaryDependencies() {
    return myLibraries;
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
    return BuildProcessLogger.EMPTY; // used for tests
  }

  @Override
  public void report(Message msg) {
    try {
      if (!myAllowWarnings && msg.getKind() == Message.Kind.WARNING) {
        return;
      }
      if (msg.getSource() != null) {
        myMessageSink.append(msg.getSource().getName()).append(": ");
      }
      if (msg.getKind() == Message.Kind.ERROR) {
        myHasErrors = true;
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
    return myHasErrors;
  }

  private static String truncateExtension(String filename) {
    int idx = filename.lastIndexOf('.');
    return idx >= 0? filename.substring(0, idx) : filename;
  }
}
