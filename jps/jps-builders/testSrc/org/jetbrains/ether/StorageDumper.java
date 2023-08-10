// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;

public final class StorageDumper {
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
          case 0 -> { // Initial state
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
                errors.append("Multiple storage paths specified, skipping ");
                errors.append(arg);
                errors.append("\n");
              }
              else {
                projectPath = arg;
              }
            }
          }
          case 1 -> { // -o
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
        System.out.println("Usage: <executable> [-o <output-path> | -h ] <storage-path>");
        System.out.println();
      }

      System.err.println(myErrors);
    }
  }

  public static void main(final String[] args) {
    final Env env = new Env(args);

    env.report();

    final String dataPath = env.getProjectPath();
    final String oath = env.getOutputPath();

    if (dataPath == null) {
      System.err.println("No project path specified.");
    }
    else {
      try {
        final File parent = new File(oath == null ? "" : oath);
        final File dataStorageRoot = new File(dataPath, "mappings");

        final Mappings mappings = new Mappings(dataStorageRoot, new PathRelativizerService());
        try {
          //final File outputPath = new File(parent, "snapshot-" + new SimpleDateFormat("dd-MM-yy(hh-mm-ss)").format(new Date()) + ".log");
          //FileUtil.createIfDoesntExist(outputPath);
          //final PrintStream p = new PrintStream(outputPath);
          //mappings.toStream(p);
          //p.close();
          mappings.toStream(parent);
        }
        finally {
          mappings.close();
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
