package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.AntProject;
import com.intellij.compiler.ant.taskdefs.Dirname;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
public class ModuleChunkAntProject extends Generator{
  private AntProject myAntProject;

  public ModuleChunkAntProject(Project project, ModuleChunk moduleChunk, GenerationOptions genOptions) {
    myAntProject = new AntProject(BuildProperties.getModuleChunkBuildFileName(moduleChunk), BuildProperties.getCompileTargetName(moduleChunk.getName()));
    myAntProject.add(new Dirname(BuildProperties.getModuleChunkBasedirProperty(moduleChunk), BuildProperties.propertyRef("ant.file." + BuildProperties.getModuleChunkBuildFileName(moduleChunk))));
    myAntProject.add(new ChunkBuild(project, moduleChunk, genOptions));

  }

  public void generate(PrintWriter out) throws IOException {
    writeXmlHeader(out);
    myAntProject.generate(out);
  }


}
