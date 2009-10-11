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
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.ExplodedAndJarTargetParameters;
import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.compiler.make.ExplodedAndJarBuildGenerator;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.extensions.Extensions;

public class BuildExplodedTarget extends Target {
  
  public BuildExplodedTarget(final ExplodedAndJarTargetParameters parameters,
                             final BuildRecipe buildRecipe,
                             final String description) {
    super(parameters.getBuildExplodedTargetName(), null, description, null);

    final ExplodedAndJarBuildGenerator[] generators = Extensions.getExtensions(DefaultExplodedAndJarBuildGenerator.EP_NAME);

    // reverse order to get overwriting instructions later
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      private int myInstructionCount;

      public boolean visitInstruction(final BuildInstruction instruction) throws Exception {
        for (final ExplodedAndJarBuildGenerator generator : generators) {
          final Tag[] tags = generator.generateTagsForExplodedTarget(instruction, parameters, myInstructionCount);
          if (tags != null) {
            for (final Tag tag : tags) {
              add(tag);
            }
            return true;
          }
        }
        for (final Tag tag : DefaultExplodedAndJarBuildGenerator.INSTANCE.generateTagsForExplodedTarget(instruction, parameters,
                                                                                                        myInstructionCount)) {
          add(tag);
        }
        myInstructionCount++;
        return super.visitInstruction(instruction);
      }
    }, true);
  }

}