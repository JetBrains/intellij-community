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
package org.jetbrains.jps.incremental.resources;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class ResourcesBuilder extends TargetBuilder<ResourceRootDescriptor, ResourcesTarget> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.resourses.ResourcesBuilder");
  public static final String BUILDER_NAME = "Resource Compiler";
  private static final List<StandardResourceBuilderEnabler> ourEnablers = Collections.synchronizedList(new ArrayList<StandardResourceBuilderEnabler>());

  public ResourcesBuilder() {
    super(ResourcesTargetType.ALL_TYPES);
  }

  public static void registerEnabler(StandardResourceBuilderEnabler enabler) {
    ourEnablers.add(enabler);
  }

  @Override
  public void build(@NotNull ResourcesTarget target,
                    @NotNull DirtyFilesHolder<ResourceRootDescriptor, ResourcesTarget> holder,
                    @NotNull final BuildOutputConsumer outputConsumer,
                    @NotNull final CompileContext context) throws ProjectBuildException, IOException {

    if (!isResourceProcessingEnabled(target.getModule())) {
      return;
    }

    try {
      holder.processDirtyFiles(new FileProcessor<ResourceRootDescriptor, ResourcesTarget>() {
        private final Map<ResourceRootDescriptor, Boolean> mySkippedRoots = new HashMap<ResourceRootDescriptor, Boolean>();
        public boolean apply(ResourcesTarget target, final File file, final ResourceRootDescriptor sourceRoot) throws IOException {
          Boolean isSkipped = mySkippedRoots.get(sourceRoot);
          if (isSkipped == null) {
            final File outputDir = target.getOutputDir();
            isSkipped = Boolean.valueOf(outputDir == null || FileUtil.filesEqual(outputDir, sourceRoot.getRootFile()));
            mySkippedRoots.put(sourceRoot, isSkipped);
          }
          if (isSkipped.booleanValue()) {
            return true;
          }
          try {
            copyResource(context, sourceRoot, file, outputConsumer);
          }
          catch (IOException e) {
            LOG.info(e);
            context.processMessage(
              new CompilerMessage(
                "resources", BuildMessage.Kind.ERROR, e.getMessage(), FileUtil.toSystemIndependentName(file.getPath())
              )
            );
            return false;
          }
          return !context.getCancelStatus().isCanceled();
        }
      });

      context.checkCanceled();

      context.processMessage(new ProgressMessage(""));
    }
    catch(BuildDataCorruptedException e) {
      throw e;
    }
    catch(ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e.getMessage(), e);
    }
  }

  private static boolean isResourceProcessingEnabled(JpsModule module) {
    synchronized (ourEnablers) {
      for (StandardResourceBuilderEnabler enabler : ourEnablers) {
        if (!enabler.isResourceProcessingEnabled(module)) {
          return false;
        }
      }
    }
    return true;
  }

  private static void copyResource(CompileContext context, ResourceRootDescriptor rd, File file, BuildOutputConsumer outputConsumer) throws IOException {
    final File outputRoot = rd.getTarget().getOutputDir();
    if (outputRoot == null) {
      return;
    }
    final String sourceRootPath = FileUtil.toSystemIndependentName(rd.getRootFile().getAbsolutePath());
    final String relativePath = FileUtil.getRelativePath(sourceRootPath, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = rd.getPackagePrefix();

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(FileUtil.toSystemIndependentName(outputRoot.getPath()));
    if (prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    context.processMessage(new ProgressMessage("Copying resources... [" + rd.getTarget().getModule().getName() + "]"));

    final String outputPath = targetPath.toString();
    final File targetFile = new File(outputPath);
    FileUtil.copyContent(file, targetFile);
    try {
      outputConsumer.registerOutputFile(targetFile, Collections.singletonList(file.getPath()));
    }
    catch (Exception e) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, e));
    }
  }

  @NotNull
  public String getPresentableName() {
    return "Resource Compiler";
  }

}
