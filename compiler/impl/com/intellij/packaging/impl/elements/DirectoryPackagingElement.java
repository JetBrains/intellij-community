package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.DirectoryElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class DirectoryPackagingElement extends CompositePackagingElement<DirectoryPackagingElement> {
  private String myDirectoryName;

  public DirectoryPackagingElement() {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
  }

  public DirectoryPackagingElement(String directoryName) {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
    myDirectoryName = directoryName;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new DirectoryElementPresentation(this); 
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                           @NotNull AntCopyInstructionCreator creator,
                                                           @NotNull ArtifactAntGenerationContext generationContext) {

    final List<Generator> children = new ArrayList<Generator>();
    final Generator command = creator.createSubFolderCommand(myDirectoryName);
    if (command != null) {
      children.add(command);
    }
    children.addAll(computeChildrenGenerators(resolvingContext, creator.subFolder(myDirectoryName), generationContext));
    return children;
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext) {
    computeChildrenInstructions(creator.subFolder(myDirectoryName), resolvingContext, compilerContext);
  }

  public DirectoryPackagingElement getState() {
    return this;
  }

  @Attribute("name")
  public String getDirectoryName() {
    return myDirectoryName;
  }

  public void setDirectoryName(String directoryName) {
    myDirectoryName = directoryName;
  }

  public void loadState(DirectoryPackagingElement state) {
    myDirectoryName = state.getDirectoryName();
  }


  public void rename(@NotNull String newName) {
    myDirectoryName = newName;
  }

  @Override
  public String getName() {
    return myDirectoryName;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof DirectoryPackagingElement && ((DirectoryPackagingElement)element).getDirectoryName().equals(myDirectoryName);
  }
}
