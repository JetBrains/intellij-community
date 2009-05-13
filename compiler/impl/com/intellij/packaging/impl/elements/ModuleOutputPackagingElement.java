package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.BuildProperties;
import com.intellij.openapi.module.Module;
import com.intellij.packaging.elements.ArtifactGenerationContext;
import com.intellij.packaging.elements.CopyInstructionCreator;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ModuleElementPresentation(myModuleName, findModule(context)));
  }

  @Override
  public List<? extends Generator> computeCopyInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                           @NotNull CopyInstructionCreator creator,
                                                           @NotNull ArtifactGenerationContext generationContext) {
    final String moduleOutput = BuildProperties.propertyRef(generationContext.getModuleOutputPath(myModuleName));
    return Collections.singletonList(creator.createDirectoryContentCopyInstruction(moduleOutput));
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
