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

    public CompileModuleChunkTarget(final Project project,
                                    ModuleChunk moduleChunk,
                                    VirtualFile[] sourceRoots,
                                    VirtualFile[] testSourceRoots,
                                    File baseDir,
                                    GenerationOptions genOptions) {
        final String moduleChunkName = moduleChunk.getName();
        //noinspection HardCodedStringLiteral
        final Tag compilerArgs = new Tag("compilerarg", Pair.create("line", BuildProperties.propertyRef(
                BuildProperties.getModuleChunkCompilerArgsProperty(moduleChunkName))));
        //noinspection HardCodedStringLiteral
        final Pair<String, String> classpathRef = Pair.create("refid", BuildProperties.getClasspathProperty(moduleChunkName));
        final Tag classpathTag = new Tag("classpath", classpathRef);
        //noinspection HardCodedStringLiteral
        final Tag bootclasspathTag =
                new Tag("bootclasspath", Pair.create("refid", BuildProperties.getBootClasspathProperty(moduleChunkName)));
        final PatternSetRef compilerExcludes = new PatternSetRef(BuildProperties.getExcludedFromCompilationProperty(moduleChunkName));

        final String mainTargetName = BuildProperties.getCompileTargetName(moduleChunkName);
        final @NonNls String productionTargetName = mainTargetName + ".production";
        final @NonNls String testsTargetName = mainTargetName + ".tests";

        final int modulesCount = moduleChunk.getModules().length;
        myMainTarget = new Target(mainTargetName, productionTargetName + "," + testsTargetName,
                                  CompilerBundle.message("generated.ant.build.compile.modules.main.target.comment", modulesCount,
                                                         moduleChunkName), null);
        myProductionTarget = new Target(productionTargetName, getChunkDependenciesString(moduleChunk),
                                        CompilerBundle.message("generated.ant.build.compile.modules.production.classes.target.comment",
                                                               modulesCount, moduleChunkName), null);
        myTestsTarget = new Target(testsTargetName, productionTargetName,
                                   CompilerBundle.message("generated.ant.build.compile.modules.tests.target.comment", modulesCount,
                                                          moduleChunkName), BuildProperties.PROPERTY_SKIP_TESTS);
        final ChunkCustomCompilerExtension[] customCompilers = moduleChunk.getCustomCompilers();

        if (sourceRoots.length > 0) {
            final String outputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(moduleChunkName));
            final Tag srcTag = new Tag("src", Pair.create("refid", BuildProperties.getSourcepathProperty(moduleChunkName)));
            myProductionTarget.add(new Mkdir(outputPathRef));
            createCustomCompilerTasks(project, moduleChunk, genOptions, false, customCompilers, compilerArgs, bootclasspathTag,
                                      classpathTag, compilerExcludes, srcTag, outputPathRef, myProductionTarget);
            if (customCompilers.length == 0 || genOptions.enableFormCompiler) {
                final Javac javac = new Javac(genOptions, moduleChunk, outputPathRef);
                javac.add(compilerArgs);
                javac.add(bootclasspathTag);
                javac.add(classpathTag);
                //noinspection HardCodedStringLiteral
                javac.add(srcTag);
                javac.add(compilerExcludes);
                myProductionTarget.add(javac);
            }
            myProductionTarget.add(createCopyTask(project, moduleChunk, sourceRoots, outputPathRef, baseDir, genOptions));
        }

        if (testSourceRoots.length > 0) {
            final String testOutputPathRef = BuildProperties.propertyRef(BuildProperties.getOutputPathForTestsProperty(moduleChunkName));
            final Tag srcTag = new Tag("src", Pair.create("refid", BuildProperties.getTestSourcepathProperty(moduleChunkName)));
            final Tag testClassPath = new Tag("classpath");
            testClassPath.add(new Tag("path", classpathRef));
            testClassPath.add(new PathElement(BuildProperties.propertyRef(BuildProperties.getOutputPathProperty(moduleChunkName))));
            myTestsTarget.add(new Mkdir(testOutputPathRef));
            createCustomCompilerTasks(project, moduleChunk, genOptions, true, customCompilers, compilerArgs, bootclasspathTag,
                                      testClassPath, compilerExcludes, srcTag, testOutputPathRef, myTestsTarget);
            if (customCompilers.length == 0 || genOptions.enableFormCompiler) {
                final Javac javac = new Javac(genOptions, moduleChunk, testOutputPathRef);
                javac.add(compilerArgs);
                javac.add(classpathTag);
                //noinspection HardCodedStringLiteral
                javac.add(testClassPath);
                //noinspection HardCodedStringLiteral
                javac.add(srcTag);
                javac.add(compilerExcludes);
                myTestsTarget.add(javac);
            }
            myTestsTarget.add(createCopyTask(project, moduleChunk, testSourceRoots, testOutputPathRef, baseDir, genOptions));
        }

        add(myMainTarget);
        add(myProductionTarget, 1);
        add(myTestsTarget, 1);
    }

    /**
     * Create custom compiler tasks
     *
     * @param project          the proejct
     * @param moduleChunk      the module chunkc
     * @param genOptions       generation options
     * @param compileTests     if true tests are being compiled
     * @param customCompilers  an array of custom compilers for this cunk
     * @param compilerArgs     the javac compilier arguements
     * @param bootclasspathTag the boot classpath element for the javac compiler
     * @param classpathTag     the classpath tag for the javac compiler
     * @param compilerExcludes the compiler excluded tag
     * @param srcTag           the soruce tag
     * @param outputPathRef    the output path references
     * @param target           the target where to add custom compiler
     */
    private void createCustomCompilerTasks(Project project,
                                           ModuleChunk moduleChunk,
                                           GenerationOptions genOptions,
                                           boolean compileTests,
                                           ChunkCustomCompilerExtension[] customCompilers,
                                           Tag compilerArgs,
                                           Tag bootclasspathTag,
                                           Tag classpathTag,
                                           PatternSetRef compilerExcludes,
                                           Tag srcTag,
                                           String outputPathRef,
                                           Target target) {
        if (customCompilers.length > 1) {
            target.add(new Tag("fail", Pair.create("message", CompilerBundle.message(
                    "generated.ant.build.compile.modules.fail.custom.comipilers"))));
        }
        for (ChunkCustomCompilerExtension ext : customCompilers) {
            ext.generateCustomCompile(project, moduleChunk, genOptions, compileTests, target, compilerArgs, bootclasspathTag,
                                      classpathTag, compilerExcludes, srcTag, outputPathRef);
        }
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

    private static Generator createCopyTask(final Project project,
                                            ModuleChunk chunk,
                                            VirtualFile[] sourceRoots,
                                            String toDir,
                                            File baseDir,
                                            final GenerationOptions genOptions) {
        //noinspection HardCodedStringLiteral
        final Tag filesSelector = new Tag("type", Pair.create("type", "file"));
        final PatternSetRef excludes = CompilerExcludes.isAvailable(project) ? new PatternSetRef(
                BuildProperties.getExcludedFromCompilationProperty(chunk.getName())) : null;
        final PatternSetRef resourcePatternsPatternSet = new PatternSetRef(BuildProperties.PROPERTY_COMPILER_RESOURCE_PATTERNS);
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final CompositeGenerator composite = new CompositeGenerator();
        final Map<String, Copy> outputDirToTaskMap = new HashMap<String, Copy>();
        for (final VirtualFile root : sourceRoots) {
            final String packagePrefix = fileIndex.getPackageNameByDirectory(root);
            final String targetDir =
                    packagePrefix != null && packagePrefix.length() > 0 ? toDir + "/" + packagePrefix.replace('.', '/') : toDir;
            Copy copy = outputDirToTaskMap.get(targetDir);
            if (copy == null) {
                copy = new Copy(targetDir);
                outputDirToTaskMap.put(targetDir, copy);
                composite.add(copy);
            }
            final FileSet fileSet = new FileSet(
                    GenerationUtils.toRelativePath(root, baseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions));
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
