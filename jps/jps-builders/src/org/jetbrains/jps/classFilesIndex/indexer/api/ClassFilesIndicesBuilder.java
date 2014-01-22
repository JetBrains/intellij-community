/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.classFilesIndex.indexer.api;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassWriter;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompiledClass;
import org.jetbrains.jps.incremental.instrumentation.BaseInstrumentingBuilder;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndicesBuilder extends BaseInstrumentingBuilder {
  public static final Logger LOG = Logger.getInstance(ClassFilesIndicesBuilder.class);
  private static final String PRESENTABLE_NAME = "Class-files indexer";
  private static final String PROGRESS_MESSAGE = "Indexing class-files...";
  public static final String PROPERTY_NAME = "intellij.compiler.output.index";

  private final Collection<ClassFilesIndexWriter> myIndexWriters = new ArrayList<ClassFilesIndexWriter>();

  @Override
  @SuppressWarnings("unchecked")
  public void buildStarted(final CompileContext context) {
    super.buildStarted(context);
    final boolean isEnabled = isEnabled();
    LOG.info("class files data index " + (isEnabled ? "enabled" : "disabled"));
    if (!isEnabled) {
      return;
    }
    final Set<String> enabledIndicesBuilders = ContainerUtil.newHashSet(System.getProperty(PROPERTY_NAME).split(";"));
    final boolean forcedRecompilation = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);
    final Iterable<ClassFileIndexerFactory> extensions = JpsServiceManager.getInstance().getExtensions(ClassFileIndexerFactory.class);
    int newIndicesCount = 0;
    for (final ClassFileIndexerFactory builder : extensions) {
      if (enabledIndicesBuilders.contains(builder.getClass().getName())) {
        final ClassFilesIndexWriter indexWriter = new ClassFilesIndexWriter(builder.create(), context);
        if (!indexWriter.isEmpty()) {
          myIndexWriters.add(indexWriter);
        }
        else if (forcedRecompilation) {
          newIndicesCount++;
          myIndexWriters.add(indexWriter);
        } else {
          indexWriter.close(context);
        }
      }
    }
    if (forcedRecompilation) {
      LOG.info(String.format("class files indexing: %d indices, %d new", myIndexWriters.size(), newIndicesCount));
    }
    else {
      LOG.info(String.format("class files indexing: %d indices", myIndexWriters.size()));
    }
  }

  @Override
  public void buildFinished(final CompileContext context) {
    super.buildFinished(context);
    if (!isEnabled()) {
      return;
    }
    for (final ClassFilesIndexWriter index : myIndexWriters) {
      index.close(context);
    }
    myIndexWriters.clear();
    LOG.info("class files indexing finished");
  }

  @Nullable
  @Override
  protected BinaryContent instrument(final CompileContext context,
                                     final CompiledClass compiled,
                                     final ClassReader reader,
                                     final ClassWriter writer,
                                     final InstrumentationClassFinder finder) {
    for (final ClassFilesIndexWriter index : myIndexWriters) {
      index.update(compiled.getOutputFile().getPath(), reader);
    }
    return null;
  }

  @Override
  protected boolean canInstrument(final CompiledClass compiledClass, final int classFileVersion) {
    return true;
  }

  @Override
  protected boolean isEnabled(final CompileContext context, final ModuleChunk chunk) {
    return isEnabled();
  }

  private static boolean isEnabled() {
    return System.getProperty(PROPERTY_NAME) != null;
  }

  @Override
  protected String getProgressMessage() {
    return PROGRESS_MESSAGE;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return PRESENTABLE_NAME;
  }

}