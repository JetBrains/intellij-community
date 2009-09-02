package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ModuleWithDependenciesPackagingElement extends ComplexPackagingElement<ModuleWithDependenciesPackagingElement> {
  private String myModuleName;

  public ModuleWithDependenciesPackagingElement() {
    super(ModuleWithDependenciesElementType.MODULE_WITH_DEPENDENCIES_TYPE);
  }

  public ModuleWithDependenciesPackagingElement(String moduleName) {
    super(ModuleWithDependenciesElementType.MODULE_WITH_DEPENDENCIES_TYPE);
    myModuleName = moduleName;
  }

  public List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType) {
    final Module module = findModule(context);

    List<PackagingElement<?>> elements = new ArrayList<PackagingElement<?>>();
    final PackagingElementFactory factory = PackagingElementFactory.getInstance();
    if (module != null) {
      final ModuleRootModel rootModel = context.getModulesProvider().getRootModel(module);
      for (OrderEntry entry : rootModel.getOrderEntries()) {
        if (entry instanceof ModuleSourceOrderEntry) {
          elements.add(factory.createModuleOutput(myModuleName, context.getProject()));
        }
        else if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            elements.addAll(factory.createLibraryElements(library));
          }
        }
        else if (entry instanceof ModuleOrderEntry) {
          elements.add(new ModuleWithDependenciesPackagingElement(((ModuleOrderEntry)entry).getModuleName()));
        }
      }
    }

    final List<PackagingElement<?>> substitution = new ArrayList<PackagingElement<?>>();
    for (PackagingElement<?> element : elements) {
      final String path = artifactType.getDefaultPathFor(element, context);
      if (path != null) {
        substitution.add(factory.createParentDirectories(path, element));
      }
    }
    return substitution;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ModuleElementPresentation(myModuleName, findModule(context), context) {
      @Override
      protected String getNodeText() {
        return CompilerBundle.message("node.text.0.with.dependencies", getPresentableName());
      }

      @Override
      public int getWeight() {
        return PackagingElementWeights.ARTIFACT - 10;
      }
    });
  }

  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ModuleWithDependenciesPackagingElement && myModuleName != null
           && myModuleName.equals(((ModuleWithDependenciesPackagingElement)element).getModuleName());
  }

  public ModuleWithDependenciesPackagingElement getState() {
    return this;
  }

  public void loadState(ModuleWithDependenciesPackagingElement state) {
    myModuleName = state.getModuleName();
  }

  @Nullable
  public Module findModule(PackagingElementResolvingContext context) {
    return context.getModulesProvider().getModule(myModuleName);
  }

  @Attribute("module-name")
  public String getModuleName() {
    return myModuleName;
  }

  public void setModuleName(String moduleName) {
    myModuleName = moduleName;
  }
}
