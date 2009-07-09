package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.JarsBuilder;
import com.intellij.compiler.impl.packagingCompiler.PackagingCompilerBase;
import com.intellij.compiler.impl.packagingCompiler.PackagingProcessingItem;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class IncrementalArtifactsCompiler extends PackagingCompilerBase<ArtifactsProcessingItemsBuilderContext> {
  private static final Key<List<String>> FILES_TO_DELETE_KEY = Key.create("artifacts_files_to_delete");
  private static final Key<Set<Artifact>> AFFECTED_ARTIFACTS = Key.create("affected_artifacts");
  private static final Key<ArtifactsProcessingItemsBuilderContext> BUILDER_CONTEXT_KEY = Key.create("artifacts_builder_context");

  public IncrementalArtifactsCompiler() {
    super(FILES_TO_DELETE_KEY, BUILDER_CONTEXT_KEY);
  }

  protected PackagingProcessingItem[] collectItems(ArtifactsProcessingItemsBuilderContext builderContext, final Project project) {
    final CompileContext context = builderContext.getCompileContext();
    final Artifact[] artifacts = getArtifactsToBuild(project, context.getCompileScope());
    for (Artifact artifact : artifacts) {
      final String outputPath = artifact.getOutputPath();
      if (outputPath == null || outputPath.length() == 0) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot build '" + artifact.getName() + "' artifact: output path is not specified",
                        null, -1, -1);
        continue;
      }

      collectItems(builderContext, artifact, outputPath, project);
    }
    context.putUserData(AFFECTED_ARTIFACTS, new HashSet<Artifact>(Arrays.asList(artifacts)));
    return builderContext.getProcessingItems();
  }

  private static Artifact[] getArtifactsToBuild(final Project project, final CompileScope compileScope) {
    final Artifact[] artifactsFromScope = ArtifactCompileScope.getArtifacts(compileScope);
    if (artifactsFromScope != null) {
      return artifactsFromScope;
    }
    List<Artifact> artifacts = new ArrayList<Artifact>();
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      if (artifact.isBuildOnMake()) {
        artifacts.add(artifact);
      }
    }
    return artifacts.toArray(new Artifact[artifacts.size()]);
  }

  @Override
  protected void onBuildFinished(ArtifactsProcessingItemsBuilderContext context, JarsBuilder builder, final Project project) throws Exception {
    final Set<Artifact> artifacts = context.getCompileContext().getUserData(AFFECTED_ARTIFACTS);
    for (Artifact artifact : artifacts) {
      for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
        artifact.getProperties(provider).onBuildFinished(project, artifact);
      }
    }
  }

  private static void collectItems(@NotNull ArtifactsProcessingItemsBuilderContext builderContext, @NotNull Artifact artifact, @NotNull String outputPath,
                            final Project project) {
    final CompositePackagingElement<?> rootElement = artifact.getRootElement();
    final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
    final CopyToDirectoryInstructionCreator instructionCreator =
        new CopyToDirectoryInstructionCreator(builderContext, outputPath, outputFile);
    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(project).getResolvingContext();
    rootElement.computeIncrementalCompilerInstructions(instructionCreator, resolvingContext, builderContext, artifact.getArtifactType());
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

  protected boolean doNotStartBuild(CompileContext context) {
    return false;
  }
}
