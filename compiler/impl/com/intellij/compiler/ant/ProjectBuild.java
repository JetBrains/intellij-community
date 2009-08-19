package com.intellij.compiler.ant;

import com.intellij.compiler.ant.artifacts.ArtifactsGenerator;
import com.intellij.compiler.ant.taskdefs.AntProject;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.ArtifactManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public abstract class ProjectBuild extends Generator {
  protected final Project myProject;
  private final AntProject myAntProject;

  public ProjectBuild(Project project, GenerationOptions genOptions) {
    myProject = project;
    myAntProject = new AntProject(BuildProperties.getProjectBuildFileName(myProject), BuildProperties.DEFAULT_TARGET);

    myAntProject.add(new BuildPropertiesImpl(myProject, genOptions), 1);

    // the sequence in which modules are imported is important cause output path properties for dependent modules should be defined first

    final StringBuilder alltargetNames = new StringBuilder();
    alltargetNames.append(BuildProperties.TARGET_INIT);
    alltargetNames.append(", ");
    alltargetNames.append(BuildProperties.TARGET_CLEAN);
    final ModuleChunk[] chunks = genOptions.getModuleChunks();

    if (chunks.length > 0) {
      myAntProject.add(new Comment(CompilerBundle.message("generated.ant.build.modules.section.title")), 1);

      for (final ModuleChunk chunk : chunks) {
        myAntProject.add(createModuleBuildGenerator(chunk, genOptions), 1);
        final String[] targets = ChunkBuildExtension.getAllTargets(chunk);
        for (String target : targets) {
          if (alltargetNames.length() > 0) {
            alltargetNames.append(", ");
          }
          alltargetNames.append(target);
        }
      }
    }

    final Target initTarget = new Target(BuildProperties.TARGET_INIT, null,
                                         CompilerBundle.message("generated.ant.build.initialization.section.title"), null);
    initTarget.add(new Comment(CompilerBundle.message("generated.ant.build.initialization.section.comment")));
    myAntProject.add(initTarget, 1);

    ArtifactsGenerator artifactsGenerator;
    if (ArtifactManager.useArtifacts()) {
      artifactsGenerator = new ArtifactsGenerator(project, genOptions);
    }
    else {
      artifactsGenerator = null;
    }

    myAntProject.add(new CleanProject(genOptions, artifactsGenerator), 1);

    if (artifactsGenerator != null) {
      List<Generator> generators = artifactsGenerator.generate();
      for (Generator generator : generators) {
        myAntProject.add(generator, 1);
      }

      if (alltargetNames.length() > 0) {
        alltargetNames.append(", ");
      }
      alltargetNames.append(ArtifactsGenerator.BUILD_ALL_ARTIFACTS_TARGET);
    }

    myAntProject.add(new Target(BuildProperties.TARGET_ALL, alltargetNames.toString(),
                                CompilerBundle.message("generated.ant.build.build.all.target.name"), null), 1);
  }

  public void generate(PrintWriter out) throws IOException {
    //noinspection HardCodedStringLiteral
    writeXmlHeader(out);
    myAntProject.generate(out);
  }

  protected abstract Generator createModuleBuildGenerator(final ModuleChunk chunk, GenerationOptions genOptions);

}
