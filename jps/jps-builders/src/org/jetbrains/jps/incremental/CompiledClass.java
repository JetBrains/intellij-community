// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * In-memory representation of JVM *.class file produced by a compiler.
 *
 * @see ModuleLevelBuilder.OutputConsumer#registerCompiledClass(BuildTarget, CompiledClass)
 * @author Eugene Zhuravlev
 */
public class CompiledClass extends UserDataHolderBase{
  private static final Logger LOG = Logger.getInstance(CompiledClass.class);

  private final @NotNull File myOutputFile;
  private final @NotNull Collection<File> sourceFiles;
  private final @Nullable String myClassName;
  private @NotNull BinaryContent myContent;

  private boolean myIsDirty = false;

  /**
   * @param outputFile  path where generated *.class file needs to be stored
   * @param sourceFiles paths to classes which were used to produce the JVM class (for Java language it always contains single *.java file)
   * @param className   fully qualified dot-separated name of the class
   * @param content     content which need to be written to {@code outputFile}
   */
  public CompiledClass(@NotNull File outputFile, @NotNull Collection<File> sourceFiles, @Nullable String className, @NotNull BinaryContent content) {
    myOutputFile = outputFile;
    this.sourceFiles = sourceFiles;
    myClassName = className;
    myContent = content;
    LOG.assertTrue(!this.sourceFiles.isEmpty());
  }

  public CompiledClass(@NotNull File outputFile, @NotNull File sourceFile, @Nullable String className, @NotNull BinaryContent content) {
    this(outputFile, List.of(sourceFile), className, content);
  }

  public void save() throws IOException {
    myContent.saveToFile(myOutputFile);
    myIsDirty = false;
  }

  public @NotNull File getOutputFile() {
    return myOutputFile;
  }

  public @NotNull Collection<File> getSourceFiles() {
    return sourceFiles;
  }

  public @Unmodifiable @NotNull List<String> getSourceFilesPaths() {
    if (sourceFiles.isEmpty()) {
      return List.of();
    }

    List<String> result = new ArrayList<>(sourceFiles.size());
    for (File t : sourceFiles) {
      result.add(t.getPath());
    }
    return result;
  }

  public @Nullable String getClassName() {
    return myClassName;
  }

  public @NotNull BinaryContent getContent() {
    return myContent;
  }

  public void setContent(@NotNull BinaryContent content) {
    myContent = content;
    myIsDirty = true;
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CompiledClass aClass = (CompiledClass)o;

    // on MacOS java.io.File.equals() is incorrectly case-sensitive
    if (!FileUtil.pathsEqual(myOutputFile.getPath(), aClass.myOutputFile.getPath())) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return FileUtilRt.pathHashCode(myOutputFile.getPath());
  }

  @Override
  public String toString() {
    return "CompiledClass{" +
           "myOutputFile=" + myOutputFile +
           ", mySourceFiles=" + sourceFiles +
           ", myIsDirty=" + myIsDirty +
           '}';
  }
}
