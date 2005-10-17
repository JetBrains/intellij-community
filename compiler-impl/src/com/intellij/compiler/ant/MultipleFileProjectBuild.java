package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Import;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
public class MultipleFileProjectBuild extends ProjectBuild{
  public MultipleFileProjectBuild(Project project, GenerationOptions genOptions) {
    super(project, genOptions);
  }

  protected Generator createModuleBuildGenerator(ModuleChunk chunk, GenerationOptions genOptions) {
    //noinspection HardCodedStringLiteral
    final String chunkBuildFile = BuildProperties.getModuleChunkBaseDir(chunk).getPath() + File.separator + BuildProperties.getModuleChunkBuildFileName(chunk) + ".xml";
    final File projectBaseDir = BuildProperties.getProjectBaseDir(myProject);
    final String pathToFile = GenerationUtils.toRelativePath(
      chunkBuildFile, projectBaseDir, BuildProperties.getProjectBaseDirProperty(), genOptions, !chunk.isSavePathsRelative()
    );
    return new Import(pathToFile);
  }

}
