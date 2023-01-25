// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.instrumentation;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.java.compiler.RmicCompilerOptions;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.rmi.Remote;
import java.util.*;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 */
public final class RmiStubsGenerator extends ClassProcessingBuilder {
  private static final String REMOTE_INTERFACE_NAME = Remote.class.getName().replace('.', '/');
  private static final File[] EMPTY_FILE_ARRAY = new File[0];
  private static final Key<Boolean> IS_ENABLED = Key.create("_rmic_compiler_enabled_");

  public RmiStubsGenerator() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @Override
  protected String getProgressMessage() {
    return JpsBuildBundle.message("progress.message.generating.rmi.stubs");
  }

  @Override
  public @NlsSafe String getPresentableName() {
    return "rmic";
  }

  @Override
  public void buildStarted(CompileContext context) {
    super.buildStarted(context);
    final RmicCompilerOptions rmicOptions = getOptions(context);
    IS_ENABLED.set(context, rmicOptions != null && rmicOptions.IS_EANABLED);
  }

  @Override
  protected boolean isEnabled(CompileContext context, ModuleChunk chunk) {
    return IS_ENABLED.get(context, Boolean.FALSE);
  }

  @Override
  protected ExitCode performBuild(CompileContext context, ModuleChunk chunk, InstrumentationClassFinder finder, OutputConsumer outputConsumer) {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    if (!outputConsumer.getCompiledClasses().isEmpty()) {
      final Map<ModuleBuildTarget, Collection<ClassItem>> remoteClasses = new HashMap<>();
      for (ModuleBuildTarget target : chunk.getTargets()) {
        for (CompiledClass compiledClass : outputConsumer.getTargetCompiledClasses(target)) {
          try {
            if (isRemote(compiledClass, finder)) {
              Collection<ClassItem> list = remoteClasses.get(target);
              if (list ==  null) {
                list = new ArrayList<>();
                remoteClasses.put(target, list);
              }
              list.add(new ClassItem(compiledClass));
            }
          }
          catch (IOException e) {
            context.processMessage(new CompilerMessage(getPresentableName(), e));
          }
        }
      }
      if (!remoteClasses.isEmpty()) {
        exitCode = generateRmiStubs(context, remoteClasses, chunk, outputConsumer);
      }
    }
    return exitCode;
  }

  private ExitCode generateRmiStubs(final CompileContext context,
                                    Map<ModuleBuildTarget, Collection<ClassItem>> remoteClasses,
                                    ModuleChunk chunk,
                                    OutputConsumer outputConsumer) {
    ExitCode exitCode = ExitCode.NOTHING_DONE;

    final Collection<File> classpath = ProjectPaths.getCompilationClasspath(chunk, false);
    final StringBuilder buf = new StringBuilder();
    for (File file : classpath) {
      if (buf.length() > 0) {
        buf.append(File.pathSeparator);
      }
      buf.append(file.getPath());
    }
    final String classpathString = buf.toString();
    final String rmicPath = getPathToRmic(chunk);
    final RmicCompilerOptions options = getOptions(context);
    final List<ModuleBuildTarget> targetsProcessed = new ArrayList<>(remoteClasses.size());

    for (Map.Entry<ModuleBuildTarget, Collection<ClassItem>> entry : remoteClasses.entrySet()) {
      try {
        final ModuleBuildTarget target = entry.getKey();
        final Collection<String> cmdLine = createStartupCommand(
          target, rmicPath, classpathString, options, entry.getValue()
        );
        final Process process = Runtime.getRuntime().exec(ArrayUtilRt.toStringArray(cmdLine));
        final BaseOSProcessHandler handler = new BaseOSProcessHandler(process, StringUtil.join(cmdLine, " "), null) {
          @Override
          public @NotNull Future<?> executeTask(@NotNull Runnable task) {
            return SharedThreadPool.getInstance().submit(task);
          }
        };

        final RmicOutputParser stdOutParser = new RmicOutputParser(context, getPresentableName());
        final RmicOutputParser stdErrParser = new RmicOutputParser(context, getPresentableName());
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (outputType == ProcessOutputTypes.STDOUT) {
              stdOutParser.append(event.getText());
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
              stdErrParser.append(event.getText());
            }
          }
        });
        handler.startNotify();
        handler.waitFor();
        targetsProcessed.add(target);
        if (stdErrParser.isErrorsReported() || stdOutParser.isErrorsReported()) {
          break;
        }
        else {
          final int exitValue = handler.getProcess().exitValue();
          if (exitValue != 0) {
            context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR,
                                                       JpsBuildBundle.message("build.message.rmi.stub.generation.failed")));
            break;
          }
        }
      }
      catch (IOException e) {
        context.processMessage(new CompilerMessage(getPresentableName(), e));
        break;
      }
    }

    // registering generated files
    final Map<File, File[]> fsCache = FileCollectionFactory.createCanonicalFileMap();
    for (ModuleBuildTarget target : targetsProcessed) {
      final Collection<ClassItem> items = remoteClasses.get(target);
      for (ClassItem item : items) {
        File[] children = fsCache.get(item.parentDir);
        if (children == null) {
          children = item.parentDir.listFiles();
          if (children == null) {
            children = EMPTY_FILE_ARRAY;
          }
          fsCache.put(item.parentDir, children);
        }
        final Collection<File> files = item.selectGeneratedFiles(children);
        if (!files.isEmpty()) {
          final Collection<String> sources = item.compiledClass.getSourceFilesPaths();
          for (File generated : files) {
            try {
              outputConsumer.registerOutputFile(target, generated, sources);
            }
            catch (IOException e) {
              context.processMessage(new CompilerMessage(getPresentableName(), e));
            }
          }
        }
      }
    }

    return exitCode;
  }

  private static Collection<String> createStartupCommand(final ModuleBuildTarget target,
                                                         final String compilerPath,
                                                         final String classpath,
                                                         final RmicCompilerOptions config,
                                                         final Collection<ClassItem> items) {
    final List<String> commandLine = new ArrayList<>();
    commandLine.add(compilerPath);

    if (config.DEBUGGING_INFO) {
      commandLine.add("-g");
    }
    if(config.GENERATE_IIOP_STUBS) {
      commandLine.add("-iiop");
    }
    final StringTokenizer tokenizer = new StringTokenizer(config.ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      commandLine.add(token);
    }

    commandLine.add("-classpath");
    commandLine.add(classpath);

    commandLine.add("-d");
    final File outputDir = target.getOutputDir();
    assert outputDir != null;
    commandLine.add(outputDir.getPath());

    for (ClassItem item : items) {
      commandLine.add(item.compiledClass.getClassName());
    }
    return commandLine;
  }

  private static String getPathToRmic(ModuleChunk chunk) {
    final JpsSdk<?> sdk = chunk.representativeTarget().getModule().getSdk(JpsJavaSdkType.INSTANCE);
    if (sdk != null) {
      final String executable = JpsJavaSdkType.getJavaExecutable(sdk);
      final int idx = FileUtil.toSystemIndependentName(executable).lastIndexOf("/");
      if (idx >= 0) {
        return executable.substring(0, idx) + "/rmic";
      }
    }
    return SystemProperties.getJavaHome() + "/bin/rmic";
  }

  private static boolean isRemote(CompiledClass compiled, InstrumentationClassFinder finder) throws IOException{
    try {
      final InstrumentationClassFinder.PseudoClass pseudoClass = finder.loadClass(compiled.getClassName());
      if (pseudoClass != null && !pseudoClass.isInterface()) {
        for (InstrumentationClassFinder.PseudoClass anInterface : pseudoClass.getInterfaces()) {
          if (isRemoteInterface(anInterface, REMOTE_INTERFACE_NAME)) {
            return true;
          }
        }
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  private static boolean isRemoteInterface(InstrumentationClassFinder.PseudoClass iface, final String remoteInterfaceName)
    throws IOException, ClassNotFoundException {
    if (remoteInterfaceName.equals(iface.getName())) {
      return true;
    }
    for (InstrumentationClassFinder.PseudoClass superIface : iface.getInterfaces()) {
      if (isRemoteInterface(superIface, remoteInterfaceName)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static RmicCompilerOptions getOptions(CompileContext context) {
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
    final JpsJavaCompilerOptions options = config.getCompilerOptions("Rmic");
    return ObjectUtils.tryCast(options, RmicCompilerOptions.class);
  }

  private static final class ClassItem {
    static final String[] GEN_SUFFIXES = {"_Stub.class", "_Skel.class", "_Tie.class"};
    final CompiledClass compiledClass;
    final File parentDir;
    final String baseName;

    ClassItem(CompiledClass compiledClass) {
      this.compiledClass = compiledClass;
      final File outputFile = compiledClass.getOutputFile();
      parentDir = outputFile.getParentFile();
      baseName = StringUtil.trimEnd(outputFile.getName(), ".class");
    }

    @NotNull
    public Collection<File> selectGeneratedFiles(File[] candidates) {
      if (candidates == null || candidates.length == 0) {
        return Collections.emptyList();
      }
      final Collection<File> result = new SmartList<>();
      final String[] suffixes = new String[GEN_SUFFIXES.length];
      for (int i = 0; i < GEN_SUFFIXES.length; i++) {
        suffixes[i] = baseName + GEN_SUFFIXES[i];
      }
      for (File candidate : candidates) {
        final String name = candidate.getName();
        for (String suffix : suffixes) {
          if (name.endsWith(suffix)) {
            result.add(candidate);
            break;
          }
        }
      }
      return result;
    }
  }

  private static final class RmicOutputParser extends LineOutputWriter {
    private final CompileContext myContext;
    private final @Nls String myCompilerName;
    private boolean myErrorsReported = false;

    private RmicOutputParser(CompileContext context, @Nls String name) {
      myContext = context;
      myCompilerName = name;
    }

    private boolean isErrorsReported() {
      return myErrorsReported;
    }

    @Override
    protected void lineAvailable(@NlsSafe String line) {
      if (!StringUtil.isEmpty(line)) {
        BuildMessage.Kind kind = BuildMessage.Kind.INFO;
        if (line.contains("error")) {
          kind = BuildMessage.Kind.ERROR;
          myErrorsReported = true;
        }
        else if (line.contains("warning")) {
          kind = BuildMessage.Kind.WARNING;
        }
        myContext.processMessage(new CompilerMessage(myCompilerName, kind, line));
      }
    }
  }
}
