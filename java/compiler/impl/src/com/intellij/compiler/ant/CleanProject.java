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
