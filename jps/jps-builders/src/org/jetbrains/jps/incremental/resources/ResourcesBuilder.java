package org.jetbrains.jps.incremental.resources;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class ResourcesBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.resourses.ResourcesBuilder");
  public static final String BUILDER_NAME = "resources";

  public ResourcesBuilder() {
    super(BuilderCategory.TRANSLATOR);
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
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
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws ProjectBuildException {
    final ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    assert patterns != null;
    try {
      final Ref<Boolean> doneSomething = new Ref<Boolean>(false);

      FSOperations.processFilesToRecompile(context, chunk, new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
        public boolean apply(final ModuleBuildTarget target, final File file, final JavaSourceRootDescriptor sourceRoot) throws IOException {
          if (patterns.isResourceFile(file, sourceRoot.root)) {
            try {
              context.processMessage(new ProgressMessage("Copying " + file.getPath()));
              doneSomething.set(true);
              copyResource(
                context, target.getModule(), file, sourceRoot,
                context.getProjectDescriptor().dataManager.getSourceToOutputMap(target), target.isTests()
              );
            }
            catch (IOException e) {
              LOG.info(e);
              context.processMessage(
                new CompilerMessage("Resource Compiler", BuildMessage.Kind.ERROR, e.getMessage(), FileUtil.toSystemIndependentName(file.getPath()))
              );
              return false;
            }
          }
          return true;
        }
      });

      return doneSomething.get()? ExitCode.OK : ExitCode.NOTHING_DONE;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e.getMessage(), e);
    }
  }

  private static void copyResource(CompileContext context,
                                   JpsModule module,
                                   File file,
                                   JavaSourceRootDescriptor sourceRoot,
                                   final SourceToOutputMapping outputToSourceMapping, final boolean tests) throws IOException {
    final String outputRootUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, tests);
    if (outputRootUrl == null) {
      return;
    }
    String rootPath = FileUtil.toSystemIndependentName(sourceRoot.root.getAbsolutePath());
    final String relativePath = FileUtil.getRelativePath(rootPath, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = sourceRoot.getPackagePrefix();

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(JpsPathUtil.urlToPath(outputRootUrl));
    if (prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    final String outputPath = targetPath.toString();
    FileUtil.copyContent(file, new File(outputPath));
    try {
      outputToSourceMapping.setOutput(file.getPath(), outputPath);
    }
    catch (Exception e) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, e));
    }
  }

  public String getDescription() {
    return "Resource Builder";
  }

}
