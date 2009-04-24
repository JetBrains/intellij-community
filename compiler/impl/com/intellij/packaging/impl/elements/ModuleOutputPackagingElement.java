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
  private String myName;

  public ModuleOutputPackagingElement() {
    super(ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE);
  }

  public ModuleOutputPackagingElement(String name) {
    super(ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE);
    myName = name;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new ModuleElementPresentation(myName, findModule(context));
  }

  public ModuleOutputPackagingElement getState() {
    return this;
  }

  public void loadState(ModuleOutputPackagingElement state) {
    myName = state.getName();
  }

  @Attribute("name")
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @Nullable
  public Module findModule(PackagingElementResolvingContext context) {
    return context.getModulesProvider().getModule(myName);
  }
}
