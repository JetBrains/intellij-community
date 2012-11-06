package org.jetbrains.jps.incremental.resources;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.ChunkBuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class ResourcesBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.resourses.ResourcesBuilder");
  public static final String BUILDER_NAME = "Resource Compiler";
  private static final List<StandardResourceBuilderEnabler> ourEnablers = new ArrayList<StandardResourceBuilderEnabler>();

  public ResourcesBuilder() {
    super(BuilderCategory.RESOURCES_PROCESSOR);
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

  public ExitCode build(final CompileContext context,
                        final ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        final ChunkBuildOutputConsumer outputConsumer) throws ProjectBuildException {
    final ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    assert patterns != null;
    try {
      final Ref<Boolean> doneSomething = new Ref<Boolean>(false);

      final FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget> processor = new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
        public boolean apply(ModuleBuildTarget target, final File file, final JavaSourceRootDescriptor sourceRoot) throws IOException {
          if (patterns.isResourceFile(file, sourceRoot.root)) {
            try {
              context.processMessage(new ProgressMessage("Copying " + file.getPath()));
              doneSomething.set(true);
              copyResource(context, sourceRoot, file, outputConsumer);
            }
            catch (IOException e) {
              LOG.info(e);
              context.processMessage(
                new CompilerMessage("Resource Compiler", BuildMessage.Kind.ERROR, e.getMessage(),
                                    FileUtil.toSystemIndependentName(file.getPath()))
              );
              return false;
            }
          }
          return true;
        }
      };

      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (isResourceProcessingEnabled(target.getModule())) {
          FSOperations.processFilesToRecompile(context, target, processor);
        }
      }

      return doneSomething.get()? ExitCode.OK : ExitCode.NOTHING_DONE;
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

  private static void copyResource(CompileContext context, JavaSourceRootDescriptor rd, File file, ChunkBuildOutputConsumer outputConsumer) throws IOException {
    final ModuleBuildTarget target = rd.getTarget();
    final String outputRootUrl = JpsJavaExtensionService.getInstance().getOutputUrl(target.getModule(), target.isTests());
    if (outputRootUrl == null) {
      return;
    }
    String rootPath = FileUtil.toSystemIndependentName(rd.root.getAbsolutePath());
    final String relativePath = FileUtil.getRelativePath(rootPath, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = rd.getPackagePrefix();

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(JpsPathUtil.urlToPath(outputRootUrl));
    if (prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    final String outputPath = targetPath.toString();
    FileUtil.copyContent(file, new File(outputPath));
    try {
      outputConsumer.registerOutputFile(target, outputPath, Collections.singletonList(file.getPath()));
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
