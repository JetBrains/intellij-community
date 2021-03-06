// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.resources;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class ResourcesBuilder extends TargetBuilder<ResourceRootDescriptor, ResourcesTarget> {
  private static final Logger LOG = Logger.getInstance(ResourcesBuilder.class);

  private static final List<StandardResourceBuilderEnabler> ourEnablers = Collections.synchronizedList(new ArrayList<>());

  public ResourcesBuilder() {
    super(ResourcesTargetType.ALL_TYPES);
  }

  @NotNull
  private static @Nls String getBuilderName() {
    return JpsBuildBundle.message("builder.name.resource.compiler");
  }

  public static void registerEnabler(StandardResourceBuilderEnabler enabler) {
    ourEnablers.add(enabler);
  }

  @Override
  public void build(@NotNull ResourcesTarget target,
                    @NotNull DirtyFilesHolder<ResourceRootDescriptor, ResourcesTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (!isResourceProcessingEnabled(target.getModule())) {
      return;
    }

    try {
      final Map<ResourceRootDescriptor, Boolean> skippedRoots = new HashMap<>();
      holder.processDirtyFiles((t, f, srcRoot) -> {
        Boolean isSkipped = skippedRoots.get(srcRoot);
        if (isSkipped == null) {
          File outputDir = t.getOutputDir();
          isSkipped = Boolean.valueOf(outputDir == null || FileUtil.filesEqual(outputDir, srcRoot.getRootFile()));
          skippedRoots.put(srcRoot, isSkipped);
        }
        if (isSkipped.booleanValue()) {
          return true;
        }
        try {
          copyResource(context, srcRoot, f, outputConsumer);
          return !context.getCancelStatus().isCanceled();
        }
        catch (IOException e) {
          LOG.info(e);
          context.processMessage(
            new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, e.getMessage(), FileUtil.toSystemIndependentName(f.getPath()))
          );
          return false;
        }
      });

      context.checkCanceled();

      context.processMessage(new ProgressMessage(""));
    }
    catch(BuildDataCorruptedException | ProjectBuildException e) {
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

    context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.copying.resources.0",
                                                                      rd.getTarget().getModule().getName())));
    try {
      final File targetFile = new File(targetPath.toString());
      FSOperations.copy(file, targetFile);
      outputConsumer.registerOutputFile(targetFile, Collections.singletonList(file.getPath()));
    }
    catch (Exception e) {
      context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, CompilerMessage.getTextFromThrowable(e)));
    }
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return getBuilderName();
  }
}