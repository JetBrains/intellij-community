package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import groovy.util.CharsetToolkit;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.ClasspathKind;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends ModuleLevelBuilder {
  public static final String BUILDER_NAME = "groovy";
  private final boolean myForStubs;
  private final String myBuilderName;

  public GroovyBuilder(boolean forStubs) {
    myForStubs = forStubs;
    myBuilderName = BUILDER_NAME + (forStubs ? "-stubs" : "-classes");
  }

  public String getName() {
    return myBuilderName;
  }

  public ModuleLevelBuilder.ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    ExitCode exitCode = ExitCode.OK;
    final Map<File, Module> toCompile = new HashMap<File, Module>();
    try {
      context.processFilesToRecompile(chunk, new FileProcessor() {
        @Override
        public boolean apply(Module module, File file, String sourceRoot) throws Exception {
          final String path = file.getPath();
          if (isGroovyFile(path)) { //todo file type check
            toCompile.put(file, module);
          }
          return true;
        }
      });

      if (toCompile.isEmpty()) {
        return exitCode;
      }

      final Set<String> cp = new LinkedHashSet<String>();
      //groovy_rt.jar
      // IMPORTANT! must be the first in classpath
      cp.add(ClasspathBootstrap.getResourcePath(GroovyCompilerWrapper.class).getPath());

      for (File file : context.getProjectPaths().getClasspathFiles(chunk, ClasspathKind.compile(context.isCompilingTests()), false)) {
        cp.add(FileUtil.toCanonicalPath(file.getPath()));
      }
      for (File file : context.getProjectPaths().getClasspathFiles(chunk, ClasspathKind.runtime(context.isCompilingTests()), false)) {
        cp.add(FileUtil.toCanonicalPath(file.getPath()));
      }

      final File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);
      final Module representativeModule = chunk.getModules().iterator().next();
      File moduleOutputDir = context.getProjectPaths().getModuleOutputDir(representativeModule, context.isCompilingTests());
      final File dir = myForStubs ? FileUtil.createTempDirectory(/*new File("/tmp/stubs/"), */"groovyStubs", null) : moduleOutputDir;
      assert dir != null;

      final Set<String> toCompilePaths = new LinkedHashSet<String>();
      for (File file : toCompile.keySet()) {
        toCompilePaths.add(FileUtil.toSystemIndependentName(file.getPath()));
      }
      
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
      if (!moduleOutputPath.endsWith("/")) {
        moduleOutputPath += "/";
      }
      Map<String, String> class2Src = buildClassToSourceMap(chunk, context, toCompilePaths, moduleOutputPath);

      String ideCharset = chunk.getProject().getProjectCharset();
      String encoding = !Comparing.equal(CharsetToolkit.getDefaultSystemCharset().name(), ideCharset) ? ideCharset : null;
      List<String> patchers = Collections.emptyList(); //todo patchers
      GroovycOSProcessHandler.fillFileWithGroovycParameters(
        tempFile, FileUtil.toCanonicalPath(dir.getPath()), toCompilePaths, FileUtil.toSystemDependentName(moduleOutputPath), class2Src, encoding, patchers
      );

      if (myForStubs) {
        JavaBuilder.addTempSourcePathRoot(context, dir);
      }

      // todo CompilerUtil.addLocaleOptions()
      //todo different outputs in a chunk
      //todo xmx
      //todo module jdk path
      final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
        SystemProperties.getJavaHome() + "/bin/java",
        "org.jetbrains.groovy.compiler.rt.GroovycRunner",
        Collections.<String>emptyList(), new ArrayList<String>(cp),
        Arrays.asList("-Xmx384m"/*, "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239"*/),
        Arrays.<String>asList(myForStubs ? "stubs" : "groovyc", tempFile.getPath())
      );

      deleteCorrespondingOutputFiles(context, toCompile);

      List<GroovycOSProcessHandler.OutputItem> successfullyCompiled = Collections.emptyList();
      try {
        final Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
        GroovycOSProcessHandler handler = new GroovycOSProcessHandler(process, null) {
          @Override
          protected void updateStatus(@Nullable String status) {
            context.processMessage(new ProgressMessage(status == null ? GROOVY_COMPILER_IN_OPERATION : status));
          }
        };
        handler.startNotify();
        handler.waitFor();

        successfullyCompiled = handler.getSuccessfullyCompiled();

        final List<CompilerMessage> messages = handler.getCompilerMessages();
        for (CompilerMessage message : messages) {
          context.processMessage(message);
        }

        boolean hasMessages = !messages.isEmpty();

        final StringBuffer unparsedBuffer = handler.getStdErr();
        if (unparsedBuffer.length() != 0) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, unparsedBuffer.toString()));
        }

        final int exitValue = handler.getProcess().exitValue();
        if (!hasMessages && exitValue != 0) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Internal groovyc error: code " + exitValue));
        }
      }
      finally {
        if (!myForStubs) {
          final Mappings delta = context.createDelta();
          final List<File> successfullyCompiledFiles = new ArrayList<File>();
          if (!successfullyCompiled.isEmpty()) {

            final Callbacks.Backend callback = delta.getCallback();
            final FileGeneratedEvent generatedEvent = new FileGeneratedEvent();

            for (GroovycOSProcessHandler.OutputItem item : successfullyCompiled) {
              final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
              final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
              final RootDescriptor moduleAndRoot = context.getModuleAndRoot(new File(sourcePath));
              if (moduleAndRoot != null) {
                final String moduleName = moduleAndRoot.module.getName().toLowerCase(Locale.US);
                context.getDataManager().getSourceToOutputMap(moduleName, moduleAndRoot.isTestRoot).appendData(sourcePath, outputPath);
              }
              callback.associate(outputPath, Callbacks.getDefaultLookup(sourcePath), new ClassReader(FileUtil.loadFileBytes(new File(outputPath))));
              successfullyCompiledFiles.add(new File(sourcePath));

              generatedEvent.add(moduleOutputPath, FileUtil.getRelativePath(moduleOutputPath, outputPath, '/'));
            }

            context.processMessage(generatedEvent);
          }


          final boolean needSecondPass = updateMappings(context, delta, chunk, toCompile.keySet(), successfullyCompiledFiles);
          if (needSecondPass) {
            exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
          }
        }
      }

      return exitCode;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  private static boolean isGroovyFile(String path) {
    return path.endsWith(".groovy") || path.endsWith(".gpp");
  }

  private static Map<String, String> buildClassToSourceMap(ModuleChunk chunk, CompileContext context, Set<String> toCompilePaths, String moduleOutputPath) throws Exception {
    final Map<String, String> class2Src = new HashMap<String, String>();
    for (Module module : chunk.getModules()) {
      final String moduleName = module.getName().toLowerCase(Locale.US);
      final SourceToOutputMapping srcToOut = context.getDataManager().getSourceToOutputMap(moduleName, context.isCompilingTests());
      for (String src : srcToOut.getKeys()) {
        if (!toCompilePaths.contains(src) && isGroovyFile(src)) {
          final Collection<String> outs = srcToOut.getState(src);
          if (outs != null) {
            for (String out : outs) {
              if (out.endsWith(".class") && out.startsWith(moduleOutputPath)) {
                final String className = out.substring(moduleOutputPath.length(), out.length() - ".class".length()).replace('/', '.');
                class2Src.put(className, src);
              }
            }
          }
        }
      }
    }
    return class2Src;
  }

  public String getDescription() {
    return "Groovy builder";
  }

}
