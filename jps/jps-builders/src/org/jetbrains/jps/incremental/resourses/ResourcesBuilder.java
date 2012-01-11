package org.jetbrains.jps.incremental.resourses;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.CompilerConfiguration;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class ResourcesBuilder extends Builder{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.resourses.ResourcesBuilder");
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
      final ResourcePatterns finalPatterns = patterns;
      // todo: process all files in case of rebuild or wholeModuleDirty
      // todo: otherwise avoid traverwing the whole module and use dirty file list taken from params
      context.processFilesToRecompile(chunk, new FileProcessor() {
        public boolean apply(final Module module, final File file, final String sourceRoot) throws Exception {
          if (finalPatterns.isResourceFile(file, sourceRoot)) {
            try {
              context.processMessage(new ProgressMessage("Copying " + file.getPath()));
              final String moduleName = module.getName().toLowerCase(Locale.US);
              copyResource(context, module, file, sourceRoot, context.getDataManager().getSourceToOutputMap(moduleName, context.isCompilingTests()));
            }
            catch (IOException e) {
              LOG.info(e);
              context.processMessage(new CompilerMessage("Resource Compiler", BuildMessage.Kind.ERROR, e.getMessage(), FileUtil.toSystemIndependentName(file.getPath())));
              return false;
            }
          }
          return true;
        }
      });

      return ExitCode.OK;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e.getMessage(), e);
    }
  }

  private static void copyResource(CompileContext context,
                                   Module module,
                                   File file,
                                   String sourceRoot,
                                   final SourceToOutputMapping outputToSourceMapping) throws Exception {
    final String outputRoot = context.isCompilingTests() ? module.getTestOutputPath() : module.getOutputPath();
    final String relativePath = FileUtil.getRelativePath(sourceRoot, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = module.getSourceRootPrefixes().get(sourceRoot);

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(outputRoot);
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
