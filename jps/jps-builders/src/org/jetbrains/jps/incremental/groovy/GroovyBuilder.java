package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FilesCollector;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.server.ClasspathBootstrap;

import java.io.*;
import java.net.MalformedURLException;
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

  public Builder.ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    FilesCollector collector = new FilesCollector(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.getPath().endsWith(".groovy"); //todo file type check
      }
    });
    try {
      context.processFiles(chunk, collector);
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }

    if (collector.getFiles().isEmpty()) {
      return Builder.ExitCode.OK;
    }
    
    List<String> cp = new ArrayList<String>();

    try {
      Collection<File> boot =
        context.getProjectPaths().getPlatformCompilationClasspath(chunk, context.isCompilingTests(), context.isMake());
      for (File file : boot) {
        file.toURI().toURL();
      }
      Collection<File> main =
        context.getProjectPaths().getCompilationClasspath(chunk, context.isCompilingTests(), context.isMake());
      for (File file : main) {
        cp.add(FileUtil.toSystemIndependentName(file.getPath()));
      }
      cp.add(ClasspathBootstrap.getResourcePath(GroovyCompilerWrapper.class).getPath()); //groovy_rt.jar
    }
    catch (MalformedURLException e) {
      throw new ProjectBuildException(e);
    }

    try {
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
      fillFileWithGroovycParameters(collector, tempFile, dir.getPath());

      Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
      StreamReader stdout = new StreamReader(process.getInputStream(), System.out);
      stdout.start();
      StreamReader stderr = new StreamReader(process.getErrorStream(), System.out);
      stderr.start();
      stdout.join();
      stderr.join();
      process.waitFor();
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }

    return ExitCode.OK;
  }

  private static void fillFileWithGroovycParameters(FilesCollector collector, File tempFile, final String outputDir) throws IOException {
    String fileText = "";
    for (File file : collector.getFiles()) {
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
  
  static class StreamReader extends Thread {
    private final BufferedReader myStream;
    private final PrintStream myWriter;

    StreamReader(InputStream stream, PrintStream writer) {
      myStream = new BufferedReader(new InputStreamReader(stream));
      myWriter = writer;
    }

    @Override
    public void run() {
      while (true) {
        String line = null;
        try {
          line = myStream.readLine();
        }
        catch (IOException e) {
          e.printStackTrace();  //todo change body of catch statement use File | Settings | File Templates.
        }
        if (line == null) {
          break;
        }
        
        myWriter.println(line);
      }
    }
  }
}
