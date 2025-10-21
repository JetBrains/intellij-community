// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.javac.*;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacFileData;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jetbrains.jps.util.Iterators.*;

/** @noinspection IO_FILE_USAGE*/
public class JavaCompilerRunner implements CompilerRunner {
  private static final Logger LOG = Logger.getLogger("com.intellij.tools.build.bazel.jvmIncBuilder.impl.JavaCompilerRunner");

  private static final String MODULE_INFO_FILE = "module-info.java";
  private static final String MODULE_INFO_FILE_SUFFIX = "/" + MODULE_INFO_FILE;

  private static final String USE_MODULE_PATH_ONLY_OPTION = "compiler.force.module.path";
  private static final String PATCH_MODULE_OPTION = "--patch-module";
  private static final String JAVAC_VM_OPTION_PREFIX = "-J-";
  private static final Set<String> FILTERED_OPTIONS = Set.of(
     "-d", "--boot-class-path", "-bootclasspath", "--class-path", "-classpath", "-cp", "-sourcepath", "--module-path", "-p", "--module-source-path"
  );
  private static final Set<String> FILTERED_SINGLE_OPTIONS = Set.of(
  );

  private final BuildContext myContext;
  private final List<String> myOptions;
  private final StorageManager myStorageManager;
  private final ModulePath myModulePath;
  private final Iterable<File> myClassPath;

  public JavaCompilerRunner(BuildContext context, StorageManager storageManager) {
    myContext = context;
    myOptions = getFilteredOptions(context);
    myStorageManager = storageManager;
    NodeSourcePathMapper pathMapper = context.getPathMapper();

    Collection<File> classpath = collect(map(context.getBinaryDependencies().getElements(), ns -> pathMapper.toPath(ns).toFile()), new ArrayList<>()); 
    File moduleInfoFile = findModuleInfo(context);
    if (moduleInfoFile != null || contains(myOptions, PATCH_MODULE_OPTION)) { // has modules or trying to patch a module

      final ModulePathSplitter.Result pair = new ModulePathSplitter().splitPath(
        moduleInfoFile, Set.of(), classpath, collectAdditionalRequires(myOptions)
      );

      // always add everything to ModulePath if module path usagfe is forced or '--patch-module' is explicitly specified in the command line
      final boolean useModulePathOnly = moduleInfoFile == null || Boolean.parseBoolean(System.getProperty(USE_MODULE_PATH_ONLY_OPTION));
      if (useModulePathOnly) {
        // in Java 9, named modules are not allowed to read classes from the classpath
        // moreover, the compiler requires all transitive dependencies to be on the module path
        ModulePath.Builder mpBuilder = ModulePath.newBuilder();
        for (var file : classpath) {
          mpBuilder.add(pair.getModulePath().getModuleName(file), file);
        }
        myModulePath = mpBuilder.create();
        myClassPath = Collections.emptyList();
      }
      else {
        // placing only explicitly referenced modules into the module path and the rest of deps to classpath
        myModulePath = pair.getModulePath();
        myClassPath = pair.getClasspath();
      }
    }
    else {
      myModulePath = ModulePath.EMPTY;
      myClassPath = classpath;
    }
  }

  @Override
  public String getName() {
    return "Javac Runner";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return src.toString().endsWith(".java");
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, Iterable<NodeSource> deletedSources, DiagnosticSink diagnosticSink, OutputSink outSink) throws Exception {
    if (isEmpty(sources)) {
      return ExitCode.OK;
    }
    NodeSourcePathMapper pathMapper = myContext.getPathMapper();
    OutputCollector outCollector = new OutputCollector(this, pathMapper, diagnosticSink, outSink);
    JavacCompilerTool javacTool = new JavacCompilerTool();
    // set non-null output, pointing to a non-existent dir. Need this to enable JavacFileManager creating OutputFileObjects
    Map<File, Set<File>> outputDir = Map.of(myContext.getDataDir().resolve("__temp__").toFile(), Set.of());

    // always empty in the current implementation
    List<File> platformCp = List.of();
    List<File> upgradeModulePath = List.of();
    List<File> sourcePath = List.of();

    logCompiledFiles(myContext, sources);

    // todo: revise command line options to ensure correct compilation
    try {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.log(Level.FINE, "Compiling " + myContext.getTargetName() + " options: " + myOptions + "; classpath: " + collect(myClassPath, new ArrayList<>()) + ";  platform_cp: " + platformCp);
      }
      final boolean compileOk = JavacMain.compile(
        myOptions,
        map(sources, ns -> pathMapper.toPath(ns).toFile()),
        myClassPath, platformCp, myModulePath, upgradeModulePath, sourcePath,
        outputDir, outCollector, outCollector,
        myContext::isCanceled, javacTool, new FileDataProvider(outSink)
      );

      if (outCollector.hasErrors()) {
        diagnosticSink.report(Message.create(this, Message.Kind.INFO, "Compilation finished with errors. Compiler options used: " + myOptions));
      }

      return compileOk ? ExitCode.OK : ExitCode.ERROR;
    }
    finally {
      ZipOutputBuilder outputBuilder = myStorageManager.getCompositeOutputBuilder();
      for (String generatedSourcesPath : outSink.getGeneratedOutputPaths(OutputOrigin.Kind.java, OutputFile.Kind.source)) {
        outputBuilder.deleteEntry(generatedSourcesPath); // for now, remove generated sources from the resulting artifact
      }
    }
  }

  /** @noinspection ConstantValue*/
  private static File findModuleInfo(BuildContext context) {
    @Nullable
    NodeSource moduleInfo = find(context.getSources().getElements(), ns -> ns.toString().endsWith(MODULE_INFO_FILE_SUFFIX));
    return moduleInfo != null? context.getPathMapper().toPath(moduleInfo).toFile() : null;
  }

  private static class OutputCollector implements DiagnosticOutputConsumer, OutputFileConsumer {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private final JavaCompilerRunner myOwner;
    private final DiagnosticSink myDiagnosticSink;
    private final OutputSink myOutSink;
    private final @NotNull NodeSourcePathMapper myPathMapper;
    private boolean myHasErrors;

    OutputCollector(JavaCompilerRunner owner, @NotNull NodeSourcePathMapper pathMapper, DiagnosticSink diagnosticSink, OutputSink outSink) {
      myOwner = owner;
      myDiagnosticSink = diagnosticSink;
      myOutSink = outSink;
      myPathMapper = pathMapper;
    }

    @Override
    public void save(@NotNull OutputFileObject javacOutput) {
      OutputFile.Kind kind =
        javacOutput.getKind() == JavaFileObject.Kind.CLASS? OutputFile.Kind.bytecode:
        javacOutput.getKind() == JavaFileObject.Kind.SOURCE? OutputFile.Kind.source :
        OutputFile.Kind.other;
      
      byte[] bytes;
      BinaryContent binContent = javacOutput.getContent();
      if (binContent != null) {
        byte[] buf = binContent.getBuffer();
        bytes = buf != null && binContent.getOffset() == 0 && binContent.getLength() == buf.length? buf : binContent.toByteArray();
      }
      else {
        bytes = EMPTY_BYTES;
      }

      myOutSink.addFile(
        new OutputFileImpl(javacOutput.getRelativePath(), kind, bytes, javacOutput.isGenerated()),
        OutputOrigin.create(OutputOrigin.Kind.java, collect(map(javacOutput.getSourceFiles(), myPathMapper::toNodeSource), new ArrayList<>()))
      );
    }
    @Override
    public void registerJavacFileData(JavacFileData data) {
      final Set<String> definedClasses = new HashSet<>();
      for (JavacDef def : data.getDefs()) {
        if (def instanceof JavacDef.JavacClassDef) {
          final JavacRef element = def.getDefinedElement();
          if (element instanceof JavacRef.JavacClass) {
            definedClasses.add(element.getName());
          }
        }
      }
      if (definedClasses.isEmpty()) {
        return;
      }

      Set<JavacRef> allRefs = data.getRefs().keySet();
      if (!allRefs.isEmpty()) {
        final Set<String> classImports = new HashSet<>();
        final Set<String> staticImports = new HashSet<>();
        final Map<String, List<CompilerDataSink.ConstantRef>> cRefs = new HashMap<>();

        for (JavacRef ref : allRefs) {
          final JavacRef.ImportProperties importProps = ref.getImportProperties();
          if (importProps != null) { // the reference comes from import list
            if (ref instanceof JavacRef.JavacClass) {
              classImports.add(ref.getName());
              if (importProps.isStatic() && importProps.isOnDemand()) {
                staticImports.add(ref.getName() + ".*");
              }
            }
            else {
              if (ref instanceof JavacRef.JavacField || ref instanceof JavacRef.JavacMethod) {
                staticImports.add(ref.getOwnerName() + "." + ref.getName());
              }
            }
          }
          else if (ref instanceof JavacRef.JavacField fieldRef && ref.getModifiers().contains(Modifier.FINAL)) {
            final String descriptor = fieldRef.getDescriptor();
            if (descriptor != null && definedClasses.contains(fieldRef.getContainingClass()) && !definedClasses.contains(fieldRef.getOwnerName())) {
              cRefs.computeIfAbsent(fieldRef.getContainingClass(), k -> new ArrayList<>()).add(
                CompilerDataSink.ConstantRef.create(fieldRef.getOwnerName(), fieldRef.getName(), descriptor)
              );
            }
          }
        }

        if (!classImports.isEmpty() || !staticImports.isEmpty()) {
          for (String aClass : definedClasses) {
            myOutSink.registerImports(aClass, classImports, staticImports);
          }
        }
        if (!cRefs.isEmpty()) {
          for (String aClass : definedClasses) {
            myOutSink.registerConstantReferences(aClass, cRefs.getOrDefault(aClass, List.of()));
          }
        }
      }
    }

    @Override
    public void outputLineAvailable(String line) {
      myDiagnosticSink.report(Message.stdOut(myOwner, line));
    }

    @Override
    public void javaFileLoaded(File file) {
      // empty
    }

    @Override
    public void customOutputData(String pluginId, String dataName, byte[] data) {
      // empty
    }

    public boolean hasErrors() {
      return myHasErrors;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        myHasErrors = true;
      }
      String message = diagnostic.getMessage(Locale.ENGLISH);
      StringBuilder msgBuilder = new StringBuilder(message);
      JavaFileObject source = diagnostic.getSource();
      if (source != null) {
        msgBuilder.append("\n\t").append(source.getName());
        if (diagnostic.getPosition() != Diagnostic.NOPOS) {
          msgBuilder.append(" (").append(diagnostic.getLineNumber()).append(":").append(diagnostic.getColumnNumber()).append(")");
          try {
            int start = (int)(diagnostic.getStartPosition());
            int end = (int)(diagnostic.getEndPosition());
            if (start >= 0 && end > start) {
              CharSequence charContent = source.getCharContent(true);
              if (end < charContent.length()) {
                msgBuilder.append("\n\tcode: \"").append(charContent.subSequence(start, end)).append("\"");
              }
            }
          }
          catch (Throwable ignored) {
          }
        }
      }
      myDiagnosticSink.report(Message.create(myOwner, getMessageKind(diagnostic), msgBuilder.toString()));
    }

    private static Message.Kind getMessageKind(Diagnostic<? extends JavaFileObject> diagnostic) {
      return switch (diagnostic.getKind()) {
        case ERROR -> Message.Kind.ERROR;
        case WARNING, MANDATORY_WARNING -> Message.Kind.WARNING;
        case NOTE, OTHER -> Message.Kind.INFO;
      };
    }
  }

  private static class FileDataProvider implements InputFileDataProvider {
    private final OutputExplorer myOutSink;

    FileDataProvider(OutputExplorer outSink) {
      myOutSink = outSink;
    }

    @Override
    public @Nullable Iterable<FileData> list(JavaFileManager.Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) {
      return filter(map(myOutSink.listFiles(packageName, recurse), this::createFileData), Objects::nonNull);
    }

    private @Nullable FileData createFileData(String path) {
      byte[] bytes = myOutSink.getFileContent(path);
      return bytes == null? null : new FileData() {
        @Override
        public @NotNull String getPath() {
          return path;
        }

        @Override
        public byte @NotNull [] getContent() {
          return bytes;
        }
      };
    }
  }

  private static @NotNull Collection<String> collectAdditionalRequires(Iterable<String> options) {
    // --add-reads module=other-module(,other-module)*
    // The option specifies additional modules to be considered as required by a given module.
    final Set<String> result = new HashSet<>();
    for (Iterator<String> it = options.iterator(); it.hasNext(); ) {
      final String option = it.next();
      if ("--add-reads".equalsIgnoreCase(option) && it.hasNext()) {
        final String moduleNames = substringAfter(it.next(), "=");
        if (moduleNames != null) {
          result.addAll(Arrays.asList(moduleNames.split(",")));
        }
      }
    }
    return result;
  }

  @Contract(pure = true)
  public static @Nullable String substringAfter(@NotNull String text, @NotNull String subString) {
    int i = text.indexOf(subString);
    return i >= 0? text.substring(i + subString.length()) : null;
  }

  private static @NotNull List<String> getFilteredOptions(BuildContext context) {
    List<String> options = new ArrayList<>();
    boolean skip = false;
    for (String arg : filter(context.getBuilderOptions().getJavaOptions(), a -> !FILTERED_SINGLE_OPTIONS.contains(a) && !a.startsWith(JAVAC_VM_OPTION_PREFIX))) {
      if (skip) {
        skip = false;
      }
      else if (FILTERED_OPTIONS.contains(arg)) {
        skip = true;
      }
      else {
        options.add(arg);
      }
    }
    return options;
  }

}
