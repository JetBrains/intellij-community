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
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.compiler.ant.artifacts.ArtifactsGenerator;
import com.intellij.openapi.compiler.CompilerBundle;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public class CleanProject extends Generator {
  private final Target myTarget;

  public CleanProject(GenerationOptions genOptions, ArtifactsGenerator artifactsGenerator) {
    StringBuffer dependencies = new StringBuffer();
    final ModuleChunk[] chunks = genOptions.getModuleChunks();
    for (int idx = 0; idx < chunks.length; idx++) {
      if (idx > 0) {
        dependencies.append(", ");
      }
      dependencies.append(BuildProperties.getModuleCleanTargetName(chunks[idx].getName()));
    }
    if (artifactsGenerator != null) {
      for (String target : artifactsGenerator.getCleanTargetNames()) {
        if (dependencies.length() > 0) dependencies.append(", ");
        dependencies.append(target);
      }
    }
    myTarget = new Target(BuildProperties.TARGET_CLEAN, dependencies.toString(),
                          CompilerBundle.message("generated.ant.build.clean.all.task.comment"), null);
  }

  public void generate(PrintWriter out) throws IOException {
    myTarget.generate(out);
  }
}
