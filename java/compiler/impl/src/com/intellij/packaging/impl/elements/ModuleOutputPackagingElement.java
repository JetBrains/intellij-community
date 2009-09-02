package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.BuildProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleOutputPackagingElement extends PackagingElement<ModuleOutputPackagingElement> {
  private String myModuleName;

  public ModuleOutputPackagingElement() {
    super(ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE);
  }

  public ModuleOutputPackagingElement(String moduleName) {
    super(ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE);
    myModuleName = moduleName;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ModuleElementPresentation(myModuleName, findModule(context)));
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final String moduleOutput = BuildProperties.propertyRef(generationContext.getModuleOutputPath(myModuleName));
    return Collections.singletonList(creator.createDirectoryContentCopyInstruction(moduleOutput));
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext, @NotNull ArtifactType artifactType) {
    final Module module = findModule(resolvingContext);
    if (module != null) {
      final VirtualFile output = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
      if (output != null) {
        creator.addDirectoryCopyInstructions(output);
      }
    }
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    return PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ModuleOutputPackagingElement && myModuleName != null
           && myModuleName.equals(((ModuleOutputPackagingElement)element).getModuleName());
  }

  public ModuleOutputPackagingElement getState() {
    return this;
  }

  public void loadState(ModuleOutputPackagingElement state) {
    myModuleName = state.getModuleName();
  }

  @NonNls @Override
  public String toString() {
    return "module:" + myModuleName;
  }

  @Attribute("name")
  public String getModuleName() {
    return myModuleName;
  }

  public void setModuleName(String moduleName) {
    myModuleName = moduleName;
  }

  @Nullable
  public Module findModule(PackagingElementResolvingContext context) {
    return context.getModulesProvider().getModule(myModuleName);
  }
}
