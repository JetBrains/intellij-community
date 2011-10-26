package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FileProcessor;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.OutputToSourceMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.jetbrains.jps.server.ClasspathBootstrap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends Builder {
  public static final String BUILDER_NAME = "groovy";

  public String getName() {
    return BUILDER_NAME;
  }

  public Builder.ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    final List<File> toCompile = new ArrayList<File>();
    try {
      final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME);
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
        return Builder.ExitCode.OK;
      }

      List<String> cp = new ArrayList<String>();
      for (File file : context.getProjectPaths().getCompilationClasspath(chunk, context.isCompilingTests(), !context.isMake())) {
        cp.add(FileUtil.toSystemIndependentName(file.getPath()));
      }
      cp.add(ClasspathBootstrap.getResourcePath(GroovyCompilerWrapper.class).getPath()); //groovy_rt.jar

      //Mappings delta = new Mappings();

      File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);

      List<String> cmd = new ArrayList<String>();
      cmd.add(SystemProperties.getJavaHome() + "/bin/java"); //todo module jdk path
      // todo cmd.add("-bootclasspath");
      cmd.add("-cp");
      cmd.add(StringUtil.join(cp, File.pathSeparator));
      cmd.add("org.jetbrains.groovy.compiler.rt.GroovycRunner");
      cmd.add("groovyc");
      cmd.add(tempFile.getPath());

      File dir = context.getProjectPaths().getModuleOutputDir(chunk.getModules().iterator().next(), context.isCompilingTests());
      assert dir != null;
      fillFileWithGroovycParameters(tempFile, dir.getPath(), toCompile);

      Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
      GroovycOSProcessHandler handler = new GroovycOSProcessHandler(process, null) {
        @Override
        protected void updateStatus(@Nullable String status) {
          context.processMessage(new ProgressMessage(status == null ? GROOVY_COMPILER_IN_OPERATION : status));
        }
      };
      handler.startNotify();
      handler.waitFor();

      List<CompilerMessage> messages = handler.getCompilerMessages();
      for (CompilerMessage message : messages) {
        BuildMessage.Kind kind = message.getCategory().equals(CompilerMessage.ERROR)
                                 ? BuildMessage.Kind.ERROR
                                 : message.getCategory().equals(CompilerMessage.WARNING)
                                   ? BuildMessage.Kind.WARNING
                                   : BuildMessage.Kind.INFO;
        context.processMessage(
          new org.jetbrains.jps.incremental.messages.CompilerMessage(BUILDER_NAME, kind, message.getMessage(), message.getUrl(), -1, -1, -1,
                                                                     message.getLineNum(), message.getColumnNum()));
      }

      boolean hasMessages = !messages.isEmpty();

      StringBuffer unparsedBuffer = handler.getStdErr();
      if (unparsedBuffer.length() != 0) {
        context.processMessage(
          new org.jetbrains.jps.incremental.messages.CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, unparsedBuffer.toString()));
        hasMessages = true;
      }

      final int exitCode = handler.getProcess().exitValue();
      if (!hasMessages && exitCode != 0) {
        context.processMessage(new org.jetbrains.jps.incremental.messages.CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                                                          "Internal groovyc error: code " + exitCode));
      }

      OutputToSourceMapping storage = context.getBuildDataManager().getOutputToSourceStorage();
      for (GroovycOSProcessHandler.OutputItem item : handler.getSuccessfullyCompiled()) {
        String src = item.sourcePath;
        storage.update(item.outputPath, src);
        /* todo
        final File classFile = new File(item.outputPath);
        Callbacks.Backend callback = delta.getCallback();
        callback.associate(item.getOutputPath(), Callbacks.getDefaultLookup(FileUtil.toSystemIndependentName(src)),
                            new ClassReader(FileUtil.loadFile(classFile)));
        */
        tsStorage.saveStamp(new File(src));
      }

      //todo context.getMappings().differentiate(delta, Collections.<String>emptyList(), )

      return ExitCode.OK;
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
      //todo file2Classes
      fileText += "end" + "\n";
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
