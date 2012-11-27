package org.jetbrains.jps.incremental.resources;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
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
 *         Date: 10/6/11
 */
public class ResourcesBuilder extends TargetBuilder<ResourceRootDescriptor, ResourcesTarget> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.resourses.ResourcesBuilder");
  public static final String BUILDER_NAME = "Resource Compiler";
  private static final List<StandardResourceBuilderEnabler> ourEnablers = new ArrayList<StandardResourceBuilderEnabler>();

  public ResourcesBuilder() {
    super(ResourcesTargetType.ALL_TYPES);
  }

  public static void registerEnabler(StandardResourceBuilderEnabler enabler) {
    ourEnablers.add(enabler);
  }

  @Override
  public void buildStarted(CompileContext context) {
    // init patterns
    ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    if (patterns == null) {
      ResourcePatterns.KEY.set(context, new ResourcePatterns(context.getProjectDescriptor().getProject()));
    }
  }

  @Override
  public void build(@NotNull ResourcesTarget target,
                    @NotNull DirtyFilesHolder<ResourceRootDescriptor, ResourcesTarget> holder,
                    @NotNull final BuildOutputConsumer outputConsumer,
                    @NotNull final CompileContext context) throws ProjectBuildException, IOException {

    if (!isResourceProcessingEnabled(target.getModule())) {
      return;
    }

    @Nullable
    final Map<ResourcesTarget, Set<File>> cleanedSources;
    if (context.isProjectRebuild()) {
      cleanedSources = null;
    }
    else {
      cleanedSources = BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, holder);
    }

    final ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    assert patterns != null;

    try {
      holder.processDirtyFiles(new FileProcessor<ResourceRootDescriptor, ResourcesTarget>() {
        public boolean apply(ResourcesTarget target, final File file, final ResourceRootDescriptor sourceRoot) throws IOException {
          if (patterns.isResourceFile(file, sourceRoot.getRootFile())) {
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
            finally {
              if (cleanedSources != null) {
                final Set<File> files = cleanedSources.get(target);
                if (files != null) {
                  files.remove(file);
                }
              }
            }
          }
          return !context.getCancelStatus().isCanceled();
        }
      });

      context.checkCanceled();

      if (cleanedSources != null) {
        // cleanup mapping for the files that were copied before but not copied now
        for (Map.Entry<ResourcesTarget, Set<File>> entry : cleanedSources.entrySet()) {
          final Set<File> files = entry.getValue();
          if (!files.isEmpty()) {
            final SourceToOutputMapping mapping = context.getProjectDescriptor().dataManager.getSourceToOutputMap(entry.getKey());
            for (File file : files) {
              mapping.remove(file.getPath());
            }
          }
        }
      }

      context.processMessage(new ProgressMessage(""));
    }
    catch (Exception e) {
      throw new ProjectBuildException(e.getMessage(), e);
    }
  }

  private static boolean isResourceProcessingEnabled(JpsModule module) {
    for (StandardResourceBuilderEnabler enabler : ourEnablers) {
      if (!enabler.isResourceProcessingEnabled(module)) {
        return false;
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
