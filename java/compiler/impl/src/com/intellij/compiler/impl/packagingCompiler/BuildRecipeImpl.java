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
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.deployment.DeploymentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class BuildRecipeImpl implements BuildRecipe {
  private final List<BuildInstruction> myInstructions = new ArrayList<BuildInstruction>();

  public void addInstruction(BuildInstruction instruction) {
    if (!contains(instruction)) {
      myInstructions.add(instruction);
    }
  }

  public boolean contains(final BuildInstruction instruction) {
    return myInstructions.contains(instruction);
  }

  public boolean visitInstructions(BuildInstructionVisitor visitor, boolean reverseOrder){
    try {
      return visitInstructionsWithExceptions(visitor, reverseOrder);
    }
    catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      }
      return false;
    }
  }
  public boolean visitInstructionsWithExceptions(BuildInstructionVisitor visitor, boolean reverseOrder) throws Exception {
    for (int i = reverseOrder ? myInstructions.size()-1 : 0;
         reverseOrder ? i>=0 : i < myInstructions.size();
         i += reverseOrder ? -1 : 1) {
      BuildInstruction instruction = myInstructions.get(i);
      if (!instruction.accept(visitor)) {
        return false;
      }
    }
    return true;
  }

  public void addFileCopyInstruction(@NotNull File file, boolean isDirectory, String outputRelativePath) {
    addInstruction(new FileCopyInstructionImpl(file, isDirectory, DeploymentUtil.trimForwardSlashes(outputRelativePath)));
  }

  public String toString() {
    String s = "Build recipe:";
    for (BuildInstruction buildInstruction : myInstructions) {
      s += "\n" + buildInstruction + "; ";
    }
    return s;
  }
}