/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Allows to extend the compilation process for Java modules compiled to .class files. Use {@link BuilderService} to register
 * implementations of this class. The order of execution of different module-level builders is determined by their category
 * (they're executed in the order of constants in the {@code BuilderCategory}; the order of executing different builders of
 * the same category is not determined).
 *
 * @author Eugene Zhuravlev
 * @since 9/17/11
 * @see BuilderService#createModuleLevelBuilders()
 */
public abstract class ModuleLevelBuilder extends Builder {
  private final BuilderCategory myCategory;

  protected ModuleLevelBuilder(BuilderCategory category) {
    myCategory = category;
  }

  public enum ExitCode {
    NOTHING_DONE, OK, ABORT, ADDITIONAL_PASS_REQUIRED, CHUNK_REBUILD_REQUIRED
  }

  public interface OutputConsumer {

    void registerOutputFile(@NotNull BuildTarget<?> target, File outputFile, Collection<String> sourcePaths) throws IOException;

    void registerCompiledClass(@Nullable BuildTarget<?> target, CompiledClass compiled) throws IOException;

    Collection<CompiledClass> getTargetCompiledClasses(@NotNull BuildTarget<?> target);
    @NotNull
    Map<String, CompiledClass> getCompiledClasses();

    @Nullable
    BinaryContent lookupClassBytes(String className);
  }

  /**
   * Performs the compilation actions for a single module or a chunk of cyclically dependent modules.
   *
   * @param context          compilation context (can be used to report compiler errors/warnings and to check whether the build
   *                         has been cancelled and needs to be stopped).
   * @param chunk            target to build.
   * @param dirtyFilesHolder can be used to enumerate the source files from the inputs of this target that have been modified
   *                         or deleted since the previous compilation run.
   * @param outputConsumer   receives the output files and classes produced by the build. (All output files produced by the build
   *                         need to be reported here.)
   */
  public abstract ExitCode build(CompileContext context,
                                 ModuleChunk chunk,
                                 DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                 OutputConsumer outputConsumer)
    throws ProjectBuildException, IOException;

  /**
   * @deprecated use {@link org.jetbrains.jps.builders.java.JavaBuilderExtension#shouldHonorFileEncodingForCompilation(java.io.File)} instead
   */
  public boolean shouldHonorFileEncodingForCompilation(File file) {
    return false;
  }

  /**
   * <strong>DO NOT RETURN {@code null}</strong> from implementation of this method. If some of builders returns {@code null} no filtering
   * will be performed for compatibility reasons.
   * @return list of extensions (without dot) of files which can be compiled by the builder
   */
  public List<String> getCompilableFileExtensions() {
    return null;
  }

  public final BuilderCategory getCategory() {
    return myCategory;
  }

  public void chunkBuildStarted(CompileContext context, ModuleChunk chunk) {
  }

  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
  }
}
