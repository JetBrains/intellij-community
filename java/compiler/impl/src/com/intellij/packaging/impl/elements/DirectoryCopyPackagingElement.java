package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.DirectoryCopyPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class DirectoryCopyPackagingElement extends FileOrDirectoryCopyPackagingElement<DirectoryCopyPackagingElement> {
  public DirectoryCopyPackagingElement() {
    super(PackagingElementFactoryImpl.DIRECTORY_COPY_ELEMENT_TYPE);
  }

  public DirectoryCopyPackagingElement(String directoryPath) {
    this();
    myFilePath = directoryPath;
  }

  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DirectoryCopyPresentation(myFilePath);
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                          @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final String path = generationContext.getSubstitutedPath(myFilePath);
    return Collections.singletonList((Generator)creator.createDirectoryContentCopyInstruction(path));
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext,
                                                     @NotNull ArtifactType artifactType) {
    final VirtualFile file = findFile();
    if (file != null && file.isValid() && file.isDirectory()) {
      creator.addDirectoryCopyInstructions(file, null);
    }
  }

  public DirectoryCopyPackagingElement getState() {
    return this;
  }

  public void loadState(DirectoryCopyPackagingElement state) {
    myFilePath = state.getFilePath();
  }

  @Override
  public String toString() {
    return "dir:" + myFilePath;
  }
}
