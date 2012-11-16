package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
* @author Eugene Zhuravlev
*         Date: 11/16/12
*/
class ChunkBuildOutputConsumerImpl implements ModuleLevelBuilder.OutputConsumer {
  private final CompileContext myContext;
  private Map<BuildTarget<?>, BuildOutputConsumerImpl> myTarget2Consumer = new THashMap<BuildTarget<?>, BuildOutputConsumerImpl>();
  private Map<File, byte[]> myClasses = new THashMap<File, byte[]>(FileUtil.FILE_HASHING_STRATEGY);

  public ChunkBuildOutputConsumerImpl(CompileContext context) {
    myContext = context;
  }

  public Map<File, byte[]> getClasses() {
    return myClasses;
  }

  @Override
  public void registerCompiledClass(BuildTarget<?> target, File outputFile, File sourceFile, byte[] bytecode) throws IOException {
    myClasses.put(outputFile, bytecode);
    registerOutputFile(target, outputFile, Collections.<String>singleton(sourceFile.getPath()));
  }

  @Override
  public void registerOutputFile(BuildTarget<?> target, File outputFile, Collection<String> sourcePaths) throws IOException {
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
}
