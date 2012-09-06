package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class UsageInModuleClasspath extends ProjectStructureElementUsage {
  private final StructureConfigurableContext myContext;
  private final ModuleProjectStructureElement myContainingElement;
  @Nullable private final DependencyScope myScope;
  private final ProjectStructureElement mySourceElement;
  private final Module myModule;

  public UsageInModuleClasspath(@NotNull StructureConfigurableContext context,
                                @NotNull ModuleProjectStructureElement containingElement,
                                ProjectStructureElement sourceElement,
                                @Nullable DependencyScope scope) {
    myContext = context;
    myContainingElement = containingElement;
    myScope = scope;
    myModule = containingElement.getModule();
    mySourceElement = sourceElement;
  }


  @Override
  public ProjectStructureElement getSourceElement() {
    return mySourceElement;
  }

  @Override
  public ModuleProjectStructureElement getContainingElement() {
    return myContainingElement;
  }

  public Module getModule() {
    return myModule;
  }

  @Override
  public String getPresentableName() {
    return myModule.getName();
  }

  @Override
  public PlaceInProjectStructure getPlace() {
    return new PlaceInModuleClasspath(myContext, myModule, myContainingElement, mySourceElement);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode()*31 + mySourceElement.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UsageInModuleClasspath && myModule.equals(((UsageInModuleClasspath)obj).myModule)
          && mySourceElement.equals(((UsageInModuleClasspath)obj).mySourceElement);
  }

  @Override
  public Icon getIcon() {
    return ModuleType.get(myModule).getIcon();
  }

  @Override
  public void removeSourceElement() {
    if (mySourceElement instanceof LibraryProjectStructureElement) {
      ModuleStructureConfigurable.getInstance(myModule.getProject())
        .removeLibraryOrderEntry(myModule, ((LibraryProjectStructureElement)mySourceElement).getLibrary());
    }
  }

  @Nullable
  @Override
  public String getPresentableLocationInElement() {
    return myScope != null && myScope != DependencyScope.COMPILE ? "[" + StringUtil.decapitalize(myScope.getDisplayName()) + "]" : null;
  }

  @Override
  public void replaceElement(final ProjectStructureElement newElement) {
    final ModuleEditor editor = myContext.getModulesConfigurator().getModuleEditor(myModule);
    if (editor != null) {
      final ModifiableRootModel rootModel = editor.getModifiableRootModelProxy();
      OrderEntryUtil.replaceLibrary(rootModel, ((LibraryProjectStructureElement)mySourceElement).getLibrary(),
                                    ((LibraryProjectStructureElement)newElement).getLibrary());
      myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, myModule));
    }
  }
}
