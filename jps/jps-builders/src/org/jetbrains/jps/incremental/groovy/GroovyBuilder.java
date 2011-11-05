package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
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
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.OutputToSourceMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends Builder {
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

  public Builder.ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    ExitCode exitCode = ExitCode.OK;
    final List<File> toCompile = new ArrayList<File>();
    try {
      final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(getName());
      context.processFiles(chunk, new FileProcessor() {
        @Override
        public boolean apply(Module module, File file, String sourceRoot) throws Exception {
          final String path = file.getPath();
          if ((path.endsWith(".groovy") || path.endsWith(".gpp")) && isFileDirty(file, context, tsStorage)) { //todo file type check
            toCompile.add(file);
          }
          return true;
        }
      });

      if (toCompile.isEmpty()) {
        return exitCode;
      }

      final List<String> cp = new ArrayList<String>();
      //groovy_rt.jar
      // IMPORTANT! must be the first in classpath
      cp.add(ClasspathBootstrap.getResourcePath(GroovyCompilerWrapper.class).getPath());

      for (File file : context.getProjectPaths().getClasspathFiles(chunk, ClasspathKind.compile(context.isCompilingTests()), false)) {
        cp.add(FileUtil.toCanonicalPath(file.getPath()));
      }

      final File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);
      final Module representativeModule = chunk.getModules().iterator().next();
      File moduleOutputDir = context.getProjectPaths().getModuleOutputDir(representativeModule, context.isCompilingTests());
      final File dir = myForStubs ? FileUtil.createTempDirectory(/*new File("/tmp/stubs/"), */"groovyStubs", null) : moduleOutputDir;
      assert dir != null;

      Set<String> toCompilePaths = new LinkedHashSet<String>();
      for (File file : toCompile) {
        toCompilePaths.add(file.getPath());
      }
      
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
      if (!moduleOutputPath.endsWith("/")) {
        moduleOutputPath += "/";
      }
      Map<String, String> class2Src = buildClassToSourceMap(context, toCompilePaths, moduleOutputPath);

      fillFileWithGroovycParameters(tempFile, FileUtil.toCanonicalPath(dir.getPath()), toCompilePaths, moduleOutputPath, class2Src);

      if (myForStubs) {
        JavaBuilder.addTempSourcePathRoot(context, dir);
      }

      // todo cmd.add("-bootclasspath");
      //todo module jdk path
      final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
        SystemProperties.getJavaHome() + "/bin/java",
        "org.jetbrains.groovy.compiler.rt.GroovycRunner",
        Collections.<String>emptyList(), cp,
        Arrays.asList("-Xmx384m"/*, "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5858"*/),
        Arrays.<String>asList(myForStubs ? "stubs" : "groovyc", tempFile.getPath())
      );

      context.deleteCorrespondingClasses(toCompile);

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
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, unparsedBuffer.toString()));
          hasMessages = true;
        }

        final int exitValue = handler.getProcess().exitValue();
        if (!hasMessages && exitValue != 0) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Internal groovyc error: code " + exitValue));
        }
      }
      finally {
        if (myForStubs) {
          for (GroovycOSProcessHandler.OutputItem item : successfullyCompiled) {
            tsStorage.saveStamp(new File(item.sourcePath));
          }
        }
        else {
          final Mappings delta = context.createDelta();
          final List<File> successfullyCompiledFiles = new ArrayList<File>();
          if (!successfullyCompiled.isEmpty()) {
            final Callbacks.Backend callback = delta.getCallback();
            final OutputToSourceMapping storage = context.getBuildDataManager().getOutputToSourceStorage();

            for (GroovycOSProcessHandler.OutputItem item : successfullyCompiled) {
              final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
              final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
              storage.update(outputPath, sourcePath);
              callback.associate(outputPath, Callbacks.getDefaultLookup(sourcePath), new ClassReader(FileUtil.loadFileBytes(new File(outputPath))));
              successfullyCompiledFiles.add(new File(sourcePath));
            }
          }

          final boolean needSecondPass = updateMappings(context, delta, chunk, toCompile, successfullyCompiledFiles);
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

  private static Map<String, String> buildClassToSourceMap(CompileContext context, Set<String> toCompilePaths, String moduleOutputPath)
    throws Exception {
    Map<String, String> class2Src = new HashMap<String, String>();
    for (String out : context.getBuildDataManager().getOutputToSourceStorage().getKeys()) {
      if (out.endsWith(".class") && out.startsWith(moduleOutputPath)) {
        String src = context.getBuildDataManager().getOutputToSourceStorage().getState(out);
        if (!toCompilePaths.contains(src)) {
          String className = out.substring(moduleOutputPath.length(), out.length() - ".class".length()).replace('/', '.').replace('$', '.');
          class2Src.put(className, src);
        }
      }
    }
    return class2Src;
  }

  private static void fillFileWithGroovycParameters(File tempFile, final String outputDir, final Collection<String> files, String finalOutput, Map<String, String> class2Src) throws IOException {
    final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
    try {
      for (String file : files) {
        writer.write("src_file\n");
        writer.write(file);
        writer.write("\n");
      }
      
      writer.write("class2src\n");
      for (Map.Entry<String, String> entry : class2Src.entrySet()) {
        writer.write(entry.getKey() + "\n");
        writer.write(entry.getValue() + "\n");
      }
      writer.write("end\n");

      //todo patchers
      writer.write("encoding\n");
      writer.write("UTF-8\n");  //todo encoding
      writer.write("outputpath\n");
      writer.write(outputDir);
      writer.write("\n");
      writer.write("final_outputpath\n");
      writer.write(finalOutput);
      writer.write("\n");
    }
    finally {
      writer.close();
    }
  }

  public String getDescription() {
    return "Groovy builder";
  }

}
