package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
* @author Eugene Zhuravlev
*         Date: 11/16/12
*/
public class BuildOutputConsumerImpl implements BuildOutputConsumer {
  private final BuildTarget<?> myTarget;
  private final CompileContext myContext;
  private FileGeneratedEvent myFileGeneratedEvent;
  private Collection<File> myOutputs;
  private THashSet<String> myRegisteredSources = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

  public BuildOutputConsumerImpl(BuildTarget<?> target, CompileContext context) {
    myTarget = target;
    myContext = context;
    myFileGeneratedEvent = new FileGeneratedEvent();
    myOutputs = myTarget.getOutputRoots(context);
  }

  @Override
  public void registerOutputFile(final File outputFile, Collection<String> sourcePaths) throws IOException {
    final String outputFilePath = FileUtil.toSystemIndependentName(outputFile.getPath());
    for (File outputRoot : myOutputs) {
      String outputRootPath = FileUtil.toSystemIndependentName(outputRoot.getPath());
      final String relativePath = FileUtil.getRelativePath(outputRootPath, outputFilePath, '/');
      if (relativePath != null) {
        myFileGeneratedEvent.add(outputRootPath, relativePath);
      }
    }
    final SourceToOutputMapping mapping = myContext.getProjectDescriptor().dataManager.getSourceToOutputMap(myTarget);
    for (String sourcePath : sourcePaths) {
      if (myRegisteredSources.add(FileUtil.toSystemIndependentName(sourcePath))) {
        mapping.setOutput(sourcePath, outputFilePath);
      }
      else {
        mapping.appendOutput(sourcePath, outputFilePath);
      }
    }
  }

  public void fireFileGeneratedEvent() {
    if (!myFileGeneratedEvent.getPaths().isEmpty()) {
      myContext.processMessage(myFileGeneratedEvent);
    }
  }
}
