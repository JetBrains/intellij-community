/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.FileCopyInstruction;
import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class FileCopyInstructionImpl extends BuildInstructionBase implements FileCopyInstruction {
  private File myFile;
  private boolean myIsDirectory;
  // for a directory keep the subset of changed files that need to be copied
  private List<FileCopyInstructionImpl> myChangedSet;
  private @Nullable final PackagingFileFilter myFileFilter;

  protected FileCopyInstructionImpl(File source,
                                 boolean isDirectory,
                                 Module module,
                                 String outputRelativePath) {
    this(source, isDirectory, module, outputRelativePath, null);
  }

  public FileCopyInstructionImpl(File source,
                                 boolean isDirectory,
                                 Module module,
                                 String outputRelativePath,
                                 @Nullable final PackagingFileFilter fileFilter) {
    super(outputRelativePath, module);
    myFileFilter = fileFilter;
    setFile(source, isDirectory);
  }

  @Nullable
  public PackagingFileFilter getFileFilter() {
    return myFileFilter;
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitFileCopyInstruction(this);
  }

  public String toString() {
    if (myChangedSet == null) {
      if (getModule() != null) {
        return CompilerBundle.message("file.copy.instruction.file.from.module.to.file.message.text", getFile(),
                                  ModuleUtil.getModuleNameInReadAction(getModule()), getOutputRelativePath());
      } else {
        return CompilerBundle.message("file.copy.instruction.file.to.file.message.text", getFile(), getOutputRelativePath());
      }
    }
    else {
      StringBuilder builder = new StringBuilder(CompilerBundle.message("file.copy.instruction.message.text", myFile));
      for (FileCopyInstruction fileCopyInstruction : myChangedSet) {
        builder.append(fileCopyInstruction).append(", ");
      }
      return builder.toString();
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileCopyInstruction)) return false;

    final FileCopyInstruction item = (FileCopyInstruction) o;

    if (getFile() != null ? !getFile().equals(item.getFile()) : item.getFile() != null) return false;

    if (getOutputRelativePath() != null) {
      if (!getOutputRelativePath().equals( item.getOutputRelativePath() )) return false;
    } else if ( item.getOutputRelativePath() != null ) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return (getFile() != null ? getFile().hashCode() : 0) +
           (getOutputRelativePath() != null ? getOutputRelativePath().hashCode():0);
  }

  public File getFile() {
    return myFile;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  private void setFile(File file, boolean isDirectory) {
    myFile = file;
    myIsDirectory = isDirectory;
  }

}
