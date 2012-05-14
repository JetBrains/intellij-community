package org.jetbrains.ether;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.Sdk;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.server.ClasspathBootstrap;

import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: db
 * Date: 14.05.12
 * Time: 13:02
 * To change this template use File | Settings | File Templates.
 */
public class StorageDumper {
  private static class Env {
    final String myProjectPath;
    final String myOutputPath;
    final String myErrors;
    final boolean myHelp;

    Env(final String[] args) {
      int s = 0;

      String projectPath = null;
      String outputPath = null;
      boolean help = false;

      final StringBuilder errors = new StringBuilder();

      for (final String arg : args) {
        switch (s) {
          case 0: // Initial state
            if (arg.equals("-o")) {
              s = 1;
            }
            else if (arg.equals("-h")) {
              help = true;
            }
            else if (arg.startsWith("-")) {
              errors.append("Unrecognized option: ");
              errors.append(arg);
              errors.append("\n");
            }
            else {
              if (projectPath != null) {
                errors.append("Multiple projects specified, skipping ");
                errors.append(arg);
                errors.append("\n");
              }
              else {
                projectPath = arg;
              }
            }
            break;

          case 1: // -o
            if (arg.startsWith("-")) {
              errors.append("Output path expected after \"-o\", but found: ");
              errors.append(arg);
              errors.append("\n");
            }
            else {
              if (outputPath != null) {
                errors.append("Multiple output paths specified, skipping ");
                errors.append(arg);
                errors.append("\n");
              }
              else {
                outputPath = arg;
              }
            }
            s = 0;
        }
      }

      myOutputPath = outputPath;
      myProjectPath = projectPath;
      myHelp = help;

      myErrors = errors.toString();
    }

    String getProjectPath() {
      return myProjectPath;
    }

    String getOutputPath() {
      return myOutputPath;
    }

    String getErrors() {
      return myErrors;
    }

    void report() {
      if (myHelp) {
        System.out.println("Usage: <executable> [-o <output-path> | -h ] <project-path>");
        System.out.println();
      }

      System.err.println(myErrors);
    }
  }

  public static void main(final String[] args) {
    final Env env = new Env(args);

    env.report();

    final String path = env.getProjectPath();
    final String oath = env.getOutputPath();

    if (path == null) {
      System.err.println("No project path specified.");
    }
    else {
      try {
        final String projectPath = path + File.separator + ".idea";
        final String outputPath = (oath == null ? "" : oath) + File.separator + "snapshot-" + new SimpleDateFormat("dd-MM-yy(hh:mm:ss)").format(new Date()) + ".log";
        final Project project = new Project();

        final Sdk jdk = project.createSdk("JavaSDK", "IDEA jdk", "1.6", System.getProperty("java.home"), null);
        final List<String> paths = new LinkedList<String>();

        paths.add(FileUtil.toSystemIndependentName(ClasspathBootstrap.getResourcePath(Object.class).getCanonicalPath()));

        jdk.setClasspath(paths);

        IdeaProjectLoader.loadFromPath(project, projectPath, "");

        final File dataStorageRoot = Utils.getDataStorageRoot(project);

        final Mappings mappings = new Mappings(dataStorageRoot, true);
        final PrintStream p = new PrintStream(outputPath);

        mappings.toStream(p);

        p.close();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
