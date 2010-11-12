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

/**
 * @author cdr
 */
package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public abstract class BuildInstructionBase extends UserDataHolderBase implements BuildInstruction, Cloneable {
  private final String myOutputRelativePath;
  private Collection<File> myFilesToDelete;

  protected BuildInstructionBase(String outputRelativePath) {
    myOutputRelativePath = outputRelativePath;
  }

  public String getOutputRelativePath() {
    return myOutputRelativePath;
  }

  public BuildInstructionBase clone() {
    return (BuildInstructionBase)super.clone();
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
    final File tempFile = FileUtil.createTempFile(prefix + "___", suffix);
    addFileToDelete(tempFile);
    return tempFile;
  }
}
