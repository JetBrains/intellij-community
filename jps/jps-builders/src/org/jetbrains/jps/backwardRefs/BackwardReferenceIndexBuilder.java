/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.tools.javac.util.Convert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.ClassRepr;
import org.jetbrains.jps.builders.java.dependencyView.ClassfileAnalyzer;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.java.dependencyView.UsageRepr;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class BackwardReferenceIndexBuilder extends ModuleLevelBuilder {
  private volatile Mappings myMappings;

  public BackwardReferenceIndexBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "backward-references indexer";
  }

  @Override
  public void buildStarted(CompileContext context) {
    BackwardReferenceIndexWriter.initialize(context);
    myMappings = context.getProjectDescriptor().dataManager.getMappings();
  }

  @Override
  public void buildFinished(CompileContext context) {
    BackwardReferenceIndexWriter.closeIfNeed();
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    if (dirtyFilesHolder.hasRemovedFiles()) {
      final BackwardReferenceIndexWriter writer = BackwardReferenceIndexWriter.getInstance();
      if (writer != null) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          final Collection<String> files = dirtyFilesHolder.getRemovedFiles(target);
          writer.processDeletedFiles(files);
        }
      }
    }

    final BackwardReferenceIndexWriter writer = BackwardReferenceIndexWriter.getInstance();
    if (writer != null) {
      for (Map.Entry<String, Collection<File>> entry : outputConsumer.getOutputFiles().entrySet()) {
        final String sourcePath = entry.getKey();
        if (sourcePath.endsWith(".kt")) {
          Map<LightRef, Void> refs = new HashMap<LightRef, Void>();

          for (File output: entry.getValue()) {
            if (!output.getName().endsWith(".class")) continue;
            final ClassfileAnalyzer analyzer = new ClassfileAnalyzer(myMappings.getDependencyContext());
            final Pair<ClassRepr, Set<UsageRepr.Usage>> analyzeResult = analyzer.analyze(0, new ClassReader(new FileInputStream(output)));


            for (UsageRepr.Usage usage : analyzeResult.getSecond()) {
              LightRef ref = null;
              if (usage instanceof UsageRepr.ClassUsage) {
                final int className = getJVMName(usage.getOwner(), writer);
                ref = new LightRef.JavaLightClassRef(className);
              }
              else if (usage instanceof UsageRepr.MethodUsage) {
                final int className = getJVMName(usage.getOwner(), writer);
                final int methodName = getJVMName(((UsageRepr.MethodUsage)usage).myName, writer);
                final int argLength = ((UsageRepr.MethodUsage)usage).myArgumentTypes.length;
                ref = new LightRef.JavaLightMethodRef(className, methodName, argLength);
              }
              else if (usage instanceof UsageRepr.FieldUsage) {
                final int className = getJVMName(usage.getOwner(), writer);
                final int fieldName = getJVMName(((UsageRepr.FieldUsage)usage).myName, writer);
                ref = new LightRef.JavaLightFieldRef(className, fieldName);
              }
              if (ref != null) {
                refs.put(ref, null);
              }
            }
          }




          //TODO
          final int pathId = writer.enumeratePath(sourcePath);
          writer.writeData(pathId, new CompiledFileData(Collections.<LightRef, Collection<LightRef>>emptyMap(), refs, Collections.<LightRef, Void>emptyMap()));
        }
      }

    }
    return null;
  }

  private int getJVMName(int id, BackwardReferenceIndexWriter writer) {
    final String s = StringUtil.replace(myMappings.valueOf(id), "/", ".");
    return writer.getByteEnumerator().enumerate((Convert.string2utf(s)));
  }
}
