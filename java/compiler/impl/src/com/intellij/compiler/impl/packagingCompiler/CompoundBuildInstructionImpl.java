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

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Collection;

public class CompoundBuildInstructionImpl extends BuildInstructionBase implements CompoundBuildInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.CompoundBuildInstructionImpl");

  private final BuildConfiguration myBuildConfiguration;
  private final BuildParticipant myBuildParticipant;
  @NonNls private static final String TMP_FILE_SUFFIX = ".tmp";

  public CompoundBuildInstructionImpl(BuildParticipant buildParticipant, String outputRelativePath) {
    super(outputRelativePath, buildParticipant.getModule());
    myBuildConfiguration = buildParticipant.getBuildConfiguration();
    myBuildParticipant = buildParticipant;
  }

  public BuildParticipant getBuildParticipant() {
    return myBuildParticipant;
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitCompoundBuildInstruction(this);
  }

  public BuildRecipe getChildInstructions(CompileContext context) {
    return myBuildParticipant.getBuildInstructions(context);
  }

  public BuildConfiguration getBuildProperties() {
    return myBuildConfiguration;
  }

  public String toString() {
    return "Java EE build instruction: " +  myBuildParticipant + " -> " + getOutputRelativePath();
  }

  public void collectFilesToDelete(final Collection<File> filesToDelete) {
    super.collectFilesToDelete(filesToDelete);
    BuildRecipe childInstructions = getChildInstructions(null);
    childInstructions.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws Exception {
        ((BuildInstructionBase)instruction).collectFilesToDelete(filesToDelete);
        return true;
      }
    }, false);
  }
}
