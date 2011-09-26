package org.jetbrains.jps.incremental.impl;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FilesCollector;
import org.jetbrains.jps.incremental.ProjectBuildException;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends Builder{
  private static final String JAVA_EXTENSION = ".java";

  private static final FileFilter JAVA_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getName().endsWith(JAVA_EXTENSION);
    }
  };

  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    final List<File> files = new ArrayList<File>();
    context.processFiles(chunk, new FilesCollector(files, JAVA_SOURCES_FILTER));
    compile(chunk, files);
    return ExitCode.FINISHED;
  }

  private void compile(ModuleChunk chunk, List<File> files) {
  }

  public String getDescription() {
    return "Java Builder";
  }

  public static void main(String[] args) {

    final ExecutorService taskRunner = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    final EmbeddedJavac javac = new EmbeddedJavac(taskRunner);

    final File srcRoot = new File("C:/tmp/MoveClassProblem/util/src");
    final List<File> sources = new ArrayList<File>();
    collectFiles(srcRoot, sources, new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".java");
      }
    });
    final File outputDir = new File("C:/tmp/MoveClassProblem/util/_out_");
    outputDir.mkdirs();

    final List<String> options = Arrays.asList(new String[] {
      //"-d",
      //outputDir.getAbsolutePath()
    });
    javac.compile(sources, options, outputDir, new EmbeddedJavac.OutputConsumer() {
      public void outputLineAvailable(String line) {
        System.out.println("OUTPUT: " + line);
      }

      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        System.out.println("DIAGNOSTIC: " + diagnostic.getKind().name() + "/" + diagnostic.getMessage(Locale.US));
      }
    });

    taskRunner.shutdownNow();
  }

  private static void collectFiles(File root, Collection<File> container, FileFilter filter) {
    final File[] files = root.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          collectFiles(file, container, filter);
        }
        else {
          if (filter.accept(file)) {
            container.add(file);
          }
        }
      }
    }
  }

}
