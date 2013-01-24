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
 * Use {@link BuilderService} to register implementations of this class
 *
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
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

    void registerOutputFile(BuildTarget<?> target, File outputFile, Collection<String> sourcePaths) throws IOException;

    void registerCompiledClass(BuildTarget<?> target, CompiledClass compiled) throws IOException;

    Collection<CompiledClass> getTargetCompiledClasses(BuildTarget<?> target);
    @NotNull
    Map<String, CompiledClass> getCompiledClasses();

    @Nullable
    BinaryContent lookupClassBytes(String className);
  }

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
