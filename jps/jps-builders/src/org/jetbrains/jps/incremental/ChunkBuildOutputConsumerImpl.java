// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.util.containers.FastUtilHashingStrategies;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.File;
import java.io.IOException;
import java.util.*;

final class ChunkBuildOutputConsumerImpl implements ModuleLevelBuilder.OutputConsumer {
  private final CompileContext myContext;
  private final Map<BuildTarget<?>, BuildOutputConsumerImpl> myTarget2Consumer = new HashMap<>();
  private final Map<String, CompiledClass> myClasses = new HashMap<>();
  private final Map<BuildTarget<?>, Collection<CompiledClass>> myTargetToClassesMap = new HashMap<>();
  private final Object2ObjectMap<File, String> myOutputToBuilderNameMap = Object2ObjectMaps.synchronize(new Object2ObjectOpenCustomHashMap<>(FastUtilHashingStrategies.FILE_HASH_STRATEGY));
  private volatile String myCurrentBuilderName;
  
  ChunkBuildOutputConsumerImpl(CompileContext context) {
    myContext = context;
  }

  public void setCurrentBuilderName(String builderName) {
    myCurrentBuilderName = builderName;
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
          classes = new ArrayList<>();
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
    final String currentBuilder = myCurrentBuilderName;
    if (currentBuilder != null) {
      final String previousBuilder = myOutputToBuilderNameMap.put(outputFile, currentBuilder);
      if (previousBuilder != null && !previousBuilder.equals(currentBuilder)) {
        final String source = sourcePaths.isEmpty()? null : sourcePaths.iterator().next();
        myContext.processMessage(new CompilerMessage(
          currentBuilder, BuildMessage.Kind.ERROR, JpsBuildBundle.message("build.message.conflicting.outputs.error", outputFile.getAbsolutePath(), previousBuilder), source
        ));
      }
    }
    BuildOutputConsumerImpl consumer = myTarget2Consumer.get(target);
    if (consumer == null) {
      consumer = new BuildOutputConsumerImpl(target, myContext);
      myTarget2Consumer.put(target, consumer);
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
    myOutputToBuilderNameMap.clear();
  }
}
