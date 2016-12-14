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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
* @author Eugene Zhuravlev
*         Date: 11/16/12
*/
class ChunkBuildOutputConsumerImpl implements ModuleLevelBuilder.OutputConsumer {
  private static final Logger LOG = Logger.getInstance(ChunkBuildOutputConsumerImpl.class);

  private final CompileContext myContext;
  private final Map<BuildTarget<?>, BuildOutputConsumerImpl> myTarget2Consumer = new THashMap<BuildTarget<?>, BuildOutputConsumerImpl>();
  private final Map<String, CompiledClass> myClasses = new THashMap<String, CompiledClass>();
  private final Map<BuildTarget<?>, Collection<CompiledClass>> myTargetToClassesMap = new THashMap<BuildTarget<?>, Collection<CompiledClass>>();
  private final MultiMap<String, File> myOutputFiles = new MultiMap<String, File>();

  public ChunkBuildOutputConsumerImpl(CompileContext context) {
    myContext = context;
  }

  @Override
  public Collection<CompiledClass> getTargetCompiledClasses(@NotNull BuildTarget<?> target) {
    final Collection<CompiledClass> classes = myTargetToClassesMap.get(target);
    if (classes != null) {
      return Collections.unmodifiableCollection(classes);
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Map<String, CompiledClass> getCompiledClasses() {
    return Collections.unmodifiableMap(myClasses);
  }

  @NotNull
  @Override
  public MultiMap<String, File> getOutputFiles() {
    return myOutputFiles;
  }

  @Override
  @Nullable
  public BinaryContent lookupClassBytes(String className) {
    final CompiledClass object = myClasses.get(className);
    return object != null ? object.getContent() : null;
  }

  @Override
  public void registerCompiledClass(@Nullable BuildTarget<?> target, CompiledClass compiled) throws IOException {
    if (compiled.getClassName() != null) {
      myClasses.put(compiled.getClassName(), compiled);
      if (target != null) {
        Collection<CompiledClass> classes = myTargetToClassesMap.get(target);
        if (classes == null) {
          classes = new ArrayList<CompiledClass>();
          myTargetToClassesMap.put(target, classes);
        }
        classes.add(compiled);
      }
    }
    if (target != null) {
      registerOutputFile(target, compiled.getOutputFile(), compiled.getSourceFilesPaths());
    }
  }

  @Override
  public void registerOutputFile(@NotNull BuildTarget<?> target, File outputFile, Collection<String> sourcePaths) throws IOException {
    BuildOutputConsumerImpl consumer = myTarget2Consumer.get(target);
    if (consumer == null) {
      consumer = new BuildOutputConsumerImpl(target, myContext);
      myTarget2Consumer.put(target, consumer);
    }
    for (String path : sourcePaths) {
      myOutputFiles.putValue(path, outputFile);
    }
    consumer.registerOutputFile(outputFile, sourcePaths);
  }

  public void fireFileGeneratedEvents() {
    for (BuildOutputConsumerImpl consumer : myTarget2Consumer.values()) {
      consumer.fireFileGeneratedEvent();
    }
  }

  public int getNumberOfProcessedSources() {
    int total = 0;
    for (BuildOutputConsumerImpl consumer : myTarget2Consumer.values()) {
      total += consumer.getNumberOfProcessedSources();
    }
    return total;
  }

  public void clear() {
    myTarget2Consumer.clear();
    myClasses.clear();
    myTargetToClassesMap.clear();
  }
}
