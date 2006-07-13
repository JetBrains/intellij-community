package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class CompileModuleChunkTarget extends CompositeGenerator {
  private final Target myMainTarget;
  private final Target myProductionTarget;
  private final Target myTestsTarget;

  public CompileModuleChunkTarget(final Project project, ModuleChunk moduleChunk, VirtualFile[] sourceRoots, VirtualFile[] testSourceRoots, File baseDir, GenerationOptions genOptions) {
    final String moduleChunkName = moduleChunk.getName();
    //noinspection HardCodedStringLiteral
    final Tag compilerArgs = new Tag("compilerarg", new Pair[]{new Pair<String, String>("line", BuildProperties.propertyRef(BuildProperties.getModuleChunkCompilerArgsProperty(moduleChunkName)))});
    //noinspection HardCodedStringLiteral
    final Tag classpathTag = new Tag("classpath", new Pair[]{new Pair<String, String>("refid", BuildProperties.getClasspathProperty(moduleChunkName))});
    //noinspection HardCodedStringLiteral
    final Tag bootclasspathTag = new Tag("bootclasspath", new Pair[]{new Pair<String, String>("refid", BuildProperties.getBootClasspathProperty(moduleChunkName))});
    final PatternSetRef compilerExcludes = new PatternSetRef(BuildProperties.getExcludedFromCompilationProperty(moduleChunkName));

    final String mainTargetName = BuildProperties.getCompileTargetName(moduleChunkName);
    final @NonNls String productionTargetName = mainTargetName + ".production";
    final @NonNls String testsTargetName = mainTargetName + ".tests";

    final int modulesCount = moduleChunk.getModules().length;
    myMainTarget = new Target(mainTargetName, productionTargetName + "," + testsTargetName,
                              CompilerBundle.message("generated.ant.build.compile.modules.main.target.comment", modulesCount, moduleChunkName), null);
    myProductionTarget = new Target(productionTargetName, getChunkDependenciesString(moduleChunk), CompilerBundle.message(
      "generated.ant.build.compile.modules.production.classes.target.comment", modulesCount, moduleChunkName), null);
    myTestsTarget = new Target(testsTargetName, productionTargetName,
                               CompilerBundle.message("generated.ant.build.compile.modules.tests.target.comment", modulesCount, moduleChunkName), BuildProperties.PROPERTY_SKIP_TESTS);

    if (sourceRoots.length > 0) {
      final String outputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(moduleChunkName));
      myProductionTarget.add(new Mkdir(outputPathRef));

      final Javac javac = new Javac(genOptions, moduleChunkName, outputPathRef);
      javac.add(compilerArgs);
      javac.add(bootclasspathTag);
      javac.add(classpathTag);
      //noinspection HardCodedStringLiteral
      javac.add(new Tag("src", new Pair[]{new Pair<String, String>("refid", BuildProperties.getSourcepathProperty(moduleChunkName))}));
      javac.add(compilerExcludes);

      myProductionTarget.add(javac);
      myProductionTarget.add(createCopyTask(project, moduleChunk, sourceRoots, outputPathRef, baseDir, genOptions));
    }

    if (testSourceRoots.length > 0) {
      final String testOutputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathForTestsProperty(moduleChunkName));
      myTestsTarget.add(new Mkdir(testOutputPathRef));

      final Javac javac = new Javac(genOptions, moduleChunkName, testOutputPathRef);
      javac.add(compilerArgs);
      javac.add(classpathTag);
      //noinspection HardCodedStringLiteral
      javac.add(new Tag("classpath", new Pair[]{new Pair<String, String>("location", BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(moduleChunkName)))}));
      //noinspection HardCodedStringLiteral
      javac.add(new Tag("src", new Pair[]{new Pair<String, String>("refid", BuildProperties.getTestSourcepathProperty(moduleChunkName))}));
      javac.add(compilerExcludes);

      myTestsTarget.add(javac);
      myTestsTarget.add(createCopyTask(project, moduleChunk, testSourceRoots, testOutputPathRef, baseDir, genOptions));
    }

    add(myMainTarget);
    add(myProductionTarget, 1);
    add(myTestsTarget, 1);
  }

  private String getChunkDependenciesString(ModuleChunk moduleChunk) {
    final StringBuffer moduleDependencies = new StringBuffer();
    final ModuleChunk[] dependencies = moduleChunk.getDependentChunks();
    for (int idx = 0; idx < dependencies.length; idx++) {
      final ModuleChunk dependency = dependencies[idx];
      if (idx > 0) {
        moduleDependencies.append(",");
      }
      moduleDependencies.append(BuildProperties.getCompileTargetName(dependency.getName()));
    }
    return moduleDependencies.toString();
  }

  private static Generator createCopyTask(final Project project, ModuleChunk chunk, VirtualFile[] sourceRoots, String toDir, File baseDir, final GenerationOptions genOptions) {
    //noinspection HardCodedStringLiteral
    final Tag filesSelector = new Tag("type", new Pair[] {new Pair("type", "file")});
    final PatternSetRef excludes = CompilerExcludes.isAvailable(project)? new PatternSetRef(BuildProperties.getExcludedFromCompilationProperty(chunk.getName())) : null;
    final PatternSetRef resourcePatternsPatternSet = new PatternSetRef(BuildProperties.PROPERTY_COMPILER_RESOURCE_PATTERNS);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompositeGenerator composite = new CompositeGenerator();
    final Map<String, Copy> outputDirToTaskMap = new HashMap<String, Copy>();
    for (final VirtualFile root : sourceRoots) {
      final String packagePrefix = fileIndex.getPackageNameByDirectory(root);
      final String targetDir = packagePrefix != null && packagePrefix.length() > 0? toDir + "/" + packagePrefix.replace('.', '/') : toDir;
      Copy copy = outputDirToTaskMap.get(targetDir);
      if (copy == null) {
        copy = new Copy(targetDir);
        outputDirToTaskMap.put(targetDir, copy);
        composite.add(copy);
      }
      final FileSet fileSet = new FileSet(GenerationUtils.toRelativePath(
        root, baseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !chunk.isSavePathsRelative())
      );
      fileSet.add(resourcePatternsPatternSet);
      fileSet.add(filesSelector);
      if (excludes != null) {
        fileSet.add(excludes);
      }
      copy.add(fileSet);
    }
    return composite;
  }
}
