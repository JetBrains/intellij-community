package com.intellij.compilerOutputIndex.api.fs;

import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

import java.io.File;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public interface FileVisitorService {

  interface Visitor {
    void visit(File file);
  }

  void visit(final Consumer<File> visitor);

  class ProjectClassFiles implements FileVisitorService {
    private final Project myProject;

    public ProjectClassFiles(final Project project) {
      myProject = project;
    }

    @Override
    public void visit(final Consumer<File> visitor) {
      CompilerOutputFilesUtil.iterateProjectClassFiles(myProject, visitor);
    }
  }

  class DirectoryClassFiles implements FileVisitorService {
    private final File myDir;

    public DirectoryClassFiles(final File dir) {
      if (!dir.isDirectory()) {
        throw new IllegalArgumentException();
      }
      myDir = dir;
    }

    @Override
    public void visit(final Consumer<File> visitor) {
      //noinspection ConstantConditions
      for (final File file : myDir.listFiles()) {
        if (file.getName().endsWith(CompilerOutputFilesUtil.CLASS_FILES_SUFFIX)) {
          visitor.consume(file);
        }
      }
    }
  }
}

