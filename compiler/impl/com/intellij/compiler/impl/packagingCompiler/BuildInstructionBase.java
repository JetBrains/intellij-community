/**
 * @author cdr
 */
package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.module.Module;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public abstract class BuildInstructionBase implements BuildInstruction, Cloneable {
  private final String myOutputRelativePath;
  private final Module myModule;
  private Collection<File> myFilesToDelete;

  protected BuildInstructionBase(String outputRelativePath, Module module) {
    myOutputRelativePath = outputRelativePath;
    myModule = module;
  }

  public String getOutputRelativePath() {
    return myOutputRelativePath;
  }

  public Module getModule() {
    return myModule;
  }

  public BuildInstructionBase clone() {
    try {
      return (BuildInstructionBase)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean isExternalDependencyInstruction() {
    return getOutputRelativePath().startsWith("..");
  }

  public void addFileToDelete(File file) {
    if (myFilesToDelete == null) {
      myFilesToDelete = new THashSet<File>();
    }
    myFilesToDelete.add(file);
  }

  public void collectFilesToDelete(Collection<File> filesToDelete) {
    if (myFilesToDelete != null) {
      filesToDelete.addAll(myFilesToDelete);
    }
    myFilesToDelete = null;
  }

  @NonNls
  public String toString() {
    return super.toString();
  }

  protected File createTempFile(final String prefix, final String suffix) throws IOException {
    final File tempFile = File.createTempFile(prefix +"___",suffix);
    addFileToDelete(tempFile);
    return tempFile;
  }
}