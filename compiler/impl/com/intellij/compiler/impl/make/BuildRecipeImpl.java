/**
 * @author cdr
 */
package com.intellij.compiler.impl.make;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.openapi.deployment.DeploymentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

  public void addAll(@NotNull BuildRecipe buildRecipe) {
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
        addInstruction(instruction);
        return true;
      }
    }, false);
  }

  public void addFileCopyInstruction(@NotNull File file,
                                     boolean isDirectory,
                                     @NotNull Module module,
                                     String outputRelativePath,
                                     @Nullable PackagingFileFilter fileFilter) {
    addInstruction(new FileCopyInstructionImpl(file, isDirectory, module, DeploymentUtil.trimForwardSlashes(outputRelativePath),fileFilter));
  }

  public String toString() {
    String s = CompilerBundle.message("message.text.build.recipe");
    for (BuildInstruction buildInstruction : myInstructions) {
      s += "\n" + buildInstruction + "; ";
    }
    return s;
  }
}