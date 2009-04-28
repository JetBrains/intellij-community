package com.intellij.packaging.impl.elements;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

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
    return new ModuleElementPresentation(myModuleName, findModule(context));
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
