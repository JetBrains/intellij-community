package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.PackagingCompilerBase;
import com.intellij.compiler.impl.packagingCompiler.PackagingProcessingItem;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class IncrementalArtifactsCompiler extends PackagingCompilerBase<ArtifactsProcessingItemsBuilderContext> {
  private static final Key<List<String>> FILES_TO_DELETE_KEY = Key.create("artifacts_files_to_delete");
  private static final Key<ArtifactsProcessingItemsBuilderContext> BUILDER_CONTEXT_KEY = Key.create("artifacts_builder_context");

  public IncrementalArtifactsCompiler(Project project) {
    super(project, FILES_TO_DELETE_KEY, BUILDER_CONTEXT_KEY);
  }

  protected PackagingProcessingItem[] collectItems(ArtifactsProcessingItemsBuilderContext builderContext) {
    final Artifact[] artifacts = ArtifactManager.getInstance(getProject()).getArtifacts();
    for (Artifact artifact : artifacts) {
      final String outputPath = artifact.getOutputPath();
      if (artifact.isBuildOnMake()) {
        if (outputPath == null || outputPath.length() == 0) {
          builderContext.getCompileContext().addMessage(CompilerMessageCategory.ERROR, "Cannot build '" + artifact.getName() + "' artifact: output path is not specified", null, -1, -1);
          continue;
        }

        collectItems(builderContext, artifact, outputPath);
      }
    }
    return builderContext.getProcessingItems();
  }

  private void collectItems(@NotNull ArtifactsProcessingItemsBuilderContext builderContext, @NotNull Artifact artifact, @NotNull String outputPath) {
    final ArtifactRootElement<?> rootElement = artifact.getRootElement();
    final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
    final CopyToDirectoryInstructionCreator instructionCreator =
        new CopyToDirectoryInstructionCreator(builderContext, outputPath, outputFile);
    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(getProject()).getResolvingContext();
    rootElement.computeIncrementalCompilerInstructions(instructionCreator, resolvingContext, builderContext);
  }

  protected ArtifactsProcessingItemsBuilderContext createContext(CompileContext context) {
    return new ArtifactsProcessingItemsBuilderContext(context);
  }

  protected String getOutputCacheId() {
    return "incremental_artifacts";
  }

  @NotNull
  public String getDescription() {
    return "Artifacts Packaging Compiler";
  }
}
