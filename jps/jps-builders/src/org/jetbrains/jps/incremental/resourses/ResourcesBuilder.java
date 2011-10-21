package org.jetbrains.jps.incremental.resourses;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.CompilerConfiguration;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class ResourcesBuilder extends Builder{
  public static final String BUILDER_NAME = "resources";

  public ResourcesBuilder() {
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  public ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    CompilerConfiguration config = null;
    for (Module module : chunk.getModules()) {
      config = module.getProject().getCompilerConfiguration();
      break;
    }

    if (config == null) {
      return ExitCode.OK;
    }

    ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    if (patterns == null) {
      ResourcePatterns.KEY.set(context, patterns = new ResourcePatterns(context.getProject()));
    }
    try {
      final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME);
      final ResourcePatterns finalPatterns = patterns;
      context.processFiles(chunk, new FileProcessor() {
        public boolean apply(final Module module, final File file, final String sourceRoot) throws Exception {
          if (finalPatterns.isResourceFile(file, sourceRoot)) {
            if (isFileDirty(file, context, tsStorage)) {
              try {
                context.processMessage(new ProgressMessage("Copying " + file.getPath()));
                copyResource(context, module, file, sourceRoot);
              }
              catch (IOException e) {
                context.processMessage(new CompilerMessage("Resource Compiler", BuildMessage.Kind.ERROR, e.getMessage(), FileUtil.toSystemIndependentName(file.getPath())));
                return false;
              }
              tsStorage.saveStamp(file);
            }
          }
          return true;
        }
      });

      //context.processMessage(new ProgressMessage("Done copying resources for " + chunk.getName()));

      return ExitCode.OK;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e.getMessage(), e);
    }
  }

  private void copyResource(CompileContext context, Module module, File file, String sourceRoot) throws IOException {
    final String outputRoot = context.isCompilingTests() ? module.getTestOutputPath() : module.getOutputPath();
    final String relativePath = FileUtil.getRelativePath(sourceRoot, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = module.getSourceRootPrefixes().get(sourceRoot);

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(outputRoot);
    if (prefix != null && prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    FileUtil.copyContent(file, new File(targetPath.toString()));
  }

  public String getDescription() {
    return "Resource Builder";
  }

}
