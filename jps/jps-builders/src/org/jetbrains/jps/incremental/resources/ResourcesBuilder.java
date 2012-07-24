package org.jetbrains.jps.incremental.resources;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

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
      ResourcePatterns.KEY.set(context, new ResourcePatterns(context.getProjectDescriptor().project));
    }
  }

  public ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    final ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    assert patterns != null;
    try {
      final Ref<Boolean> doneSomething = new Ref<Boolean>(false);
      FSOperations.processFilesToRecompile(context, chunk, new FileProcessor() {
        public boolean apply(final JpsModule module, final File file, final String sourceRoot) throws IOException {
          if (patterns.isResourceFile(file, sourceRoot)) {
            try {
              context.processMessage(new ProgressMessage("Copying " + file.getPath()));
              doneSomething.set(true);
              copyResource(
                context, module, file, sourceRoot,
                context.getProjectDescriptor().dataManager.getSourceToOutputMap(module.getName(), context.isCompilingTests())
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
                                   String sourceRoot,
                                   final SourceToOutputMapping outputToSourceMapping) throws IOException {
    final String outputRootUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, context.isCompilingTests());
    if (outputRootUrl == null) {
      return;
    }
    final String relativePath = FileUtil.getRelativePath(sourceRoot, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = JpsJavaExtensionService.getInstance().getSourcePrefix(module, JpsPathUtil.pathToUrl(sourceRoot));

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(JpsPathUtil.urlToPath(outputRootUrl));
    if (prefix != null && prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    final String outputPath = targetPath.toString();
    FileUtil.copyContent(file, new File(outputPath));
    try {
      outputToSourceMapping.update(file.getPath(), outputPath);
    }
    catch (Exception e) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, e));
    }
  }

  public String getDescription() {
    return "Resource Builder";
  }

}
