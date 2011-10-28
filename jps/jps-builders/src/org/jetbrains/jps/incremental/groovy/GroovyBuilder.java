package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.OutputToSourceMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends Builder {
  public static final String BUILDER_NAME = "groovy";
  private final boolean myForStubs;

  public GroovyBuilder(boolean forStubs) {
    myForStubs = forStubs;
  }

  public String getName() {
    return BUILDER_NAME;
  }

  public Builder.ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    ExitCode exitCode = ExitCode.OK;
    final List<File> toCompile = new ArrayList<File>();
    try {
      final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME + myForStubs);
      context.processFiles(chunk, new FileProcessor() {
        @Override
        public boolean apply(Module module, File file, String sourceRoot) throws Exception {
          if (file.getPath().endsWith(".groovy") && isFileDirty(file, context, tsStorage)) { //todo file type check
            toCompile.add(file);
          }
          return true;
        }
      });

      if (toCompile.isEmpty()) {
        return exitCode;
      }

      final List<String> cp = new ArrayList<String>();
      for (File file : context.getProjectPaths().getCompilationClasspath(chunk, context.isCompilingTests(), !context.isMake())) {
        cp.add(FileUtil.toSystemIndependentName(file.getPath()));
      }
      cp.add(ClasspathBootstrap.getResourcePath(GroovyCompilerWrapper.class).getPath()); //groovy_rt.jar

      final File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);
      final File dir = myForStubs ?
                 FileUtil.createTempDirectory(/*new File("/tmp/stubs/"), */"groovyStubs", null) : //todo clear on delete, add to javac sourcepath right now
                 context.getProjectPaths().getModuleOutputDir(chunk.getModules().iterator().next(), context.isCompilingTests());
      assert dir != null;
      fillFileWithGroovycParameters(tempFile, dir.getPath(), toCompile);

      // todo cmd.add("-bootclasspath");
      //todo module jdk path
      final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
        SystemProperties.getJavaHome() + "/bin/java",
        "org.jetbrains.groovy.compiler.rt.GroovycRunner",
        Collections.<String>emptyList(),
        cp,
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
          BuildMessage.Kind kind = message.getCategory().equals(CompilerMessage.ERROR)
                                   ? BuildMessage.Kind.ERROR
                                   : message.getCategory().equals(CompilerMessage.WARNING)
                                     ? BuildMessage.Kind.WARNING
                                     : BuildMessage.Kind.INFO;
          context.processMessage(
            new org.jetbrains.jps.incremental.messages.CompilerMessage(
              BUILDER_NAME, kind, message.getMessage(), message.getUrl(), -1, -1, -1, message.getLineNum(), message.getColumnNum())
          );
        }

        boolean hasMessages = !messages.isEmpty();

        final StringBuffer unparsedBuffer = handler.getStdErr();
        if (unparsedBuffer.length() != 0) {
          context.processMessage(
            new org.jetbrains.jps.incremental.messages.CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, unparsedBuffer.toString())
          );
          hasMessages = true;
        }

        final int exitValue = handler.getProcess().exitValue();
        if (!hasMessages && exitValue != 0) {
          context.processMessage(new org.jetbrains.jps.incremental.messages.CompilerMessage(
            BUILDER_NAME, BuildMessage.Kind.ERROR, "Internal groovyc error: code " + exitValue
          ));
        }
      }
      finally {
        final Mappings delta = new Mappings();
        final List<File> successfullyCompiledFiles = new ArrayList<File>();

        if (!successfullyCompiled.isEmpty()) {
          final Callbacks.Backend callback = delta.getCallback();
          final OutputToSourceMapping storage = context.getBuildDataManager().getOutputToSourceStorage();

          for (GroovycOSProcessHandler.OutputItem item : successfullyCompiled) {
            final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
            final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
            storage.update(outputPath, sourcePath);

            if (!myForStubs) {
              callback.associate(outputPath, Callbacks.getDefaultLookup(sourcePath), new ClassReader(FileUtil.loadFile(new File(outputPath))));
            }

            successfullyCompiledFiles.add(new File(sourcePath));
          }
        }

        final boolean needSecondPass = !myForStubs && updateMappings(context, delta, chunk, toCompile, successfullyCompiledFiles);
        if (needSecondPass) {
          exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
        }
      }

      return exitCode;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  private static void fillFileWithGroovycParameters(File tempFile, final String outputDir, final Collection<File> files) throws IOException {
    String fileText = "";
    for (File file : files) {
      fileText += "src_file\n";
      fileText += file.getPath() + "\n";
    }

    //todo patchers
    fileText += "encoding\n";
    fileText += "UTF-8\n"; //todo encoding
    fileText += "outputpath\n";
    fileText += outputDir + "\n";
    fileText += "finaloutputpath\n";
    fileText += outputDir + "\n";

    FileWriter writer = new FileWriter(tempFile);
    writer.write(fileText);
    writer.close();
  }

  public String getDescription() {
    return "Groovy builder";
  }

}
