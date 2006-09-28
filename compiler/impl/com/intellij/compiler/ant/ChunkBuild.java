package com.intellij.compiler.ant;

import com.intellij.compiler.ant.j2ee.J2EEBuildTarget;
import com.intellij.compiler.ant.j2ee.J2EEExplodedBuildTarget;
import com.intellij.compiler.ant.j2ee.J2EEJarBuildTarget;
import com.intellij.compiler.ant.taskdefs.Path;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 22, 2004
 */
public class ChunkBuild extends CompositeGenerator{

  public ChunkBuild(Project project, ModuleChunk chunk, GenerationOptions genOptions) {
    final File chunkBaseDir = chunk.getBaseDir();
    if (chunk.isJ2EEApplication()) {
      add(new CompileModuleChunkTarget(project, chunk, VirtualFile.EMPTY_ARRAY, VirtualFile.EMPTY_ARRAY, chunkBaseDir, genOptions), 1);
    }
    else {

      if (genOptions.forceTargetJdk) {
        if (chunk.isJdkInherited()) {
          add(new Property(BuildProperties.getModuleChunkJdkHomeProperty(chunk.getName()), BuildProperties.propertyRef(BuildProperties.PROPERTY_PROJECT_JDK_HOME)));
          add(new Property(BuildProperties.getModuleChunkJdkBinProperty(chunk.getName()), BuildProperties.propertyRef(BuildProperties.PROPERTY_PROJECT_JDK_BIN)));
          add(new Property(BuildProperties.getModuleChunkJdkClasspathProperty(chunk.getName()), BuildProperties.propertyRef(BuildProperties.PROPERTY_PROJECT_JDK_CLASSPATH)));
        }
        else {
          final ProjectJdk jdk = chunk.getJdk();
          add(new Property(BuildProperties.getModuleChunkJdkHomeProperty(chunk.getName()), jdk != null? BuildProperties.propertyRef(BuildProperties.getJdkHomeProperty(jdk.getName())): ""));
          add(new Property(BuildProperties.getModuleChunkJdkBinProperty(chunk.getName()), jdk != null? BuildProperties.propertyRef(BuildProperties.getJdkBinProperty(jdk.getName())): ""));
          add(new Property(BuildProperties.getModuleChunkJdkClasspathProperty(chunk.getName()), jdk != null? BuildProperties.getJdkPathId(jdk.getName()) : ""));
        }
      }

      add(new Property(BuildProperties.getModuleChunkCompilerArgsProperty(chunk.getName()), BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_ADDITIONAL_ARGS)), 1);

      final String outputPathUrl = chunk.getOutputDirUrl();
      String location = outputPathUrl != null?
                        GenerationUtils.toRelativePath(VirtualFileManager.extractPath(outputPathUrl), chunkBaseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !chunk.isSavePathsRelative()) :
                        CompilerBundle.message("value.undefined");
      add(new Property(BuildProperties.getOutputPathProperty(chunk.getName()), location), 1);

      final String testOutputPathUrl = chunk.getTestsOutputDirUrl();
      if (testOutputPathUrl != null) {
        location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(testOutputPathUrl), chunkBaseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !chunk.isSavePathsRelative());
      }
      add(new Property(BuildProperties.getOutputPathForTestsProperty(chunk.getName()), location));

      add(createBootclasspath(chunk), 1);
      add(new ModuleChunkClasspath(chunk, genOptions), 1);

      final ModuleChunkSourcepath moduleSources = new ModuleChunkSourcepath(project, chunk, genOptions);
      add(moduleSources, 1);
      add(new CompileModuleChunkTarget(project, chunk, moduleSources.getSourceRoots(), moduleSources.getTestSourceRoots(), chunkBaseDir, genOptions), 1);
    }

    add(new CleanModule(chunk), 1);

    if (chunk.isJ2EE()) {
      add(new J2EEBuildTarget(chunk, chunkBaseDir, genOptions));
      add(new J2EEExplodedBuildTarget(chunk, chunkBaseDir, genOptions));
      add(new J2EEJarBuildTarget(chunk, chunkBaseDir, genOptions));
    }
  }

  private static Generator createBootclasspath(ModuleChunk chunk) {
    final Path bootclasspath = new Path(BuildProperties.getBootClasspathProperty(chunk.getName()));
    bootclasspath.add(new Comment(CompilerBundle.message("generated.ant.build.bootclasspath.comment")));
    return bootclasspath;
  }


}
