package com.intellij.packaging.impl.elements;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.dragAndDrop.ModuleSourceItemsProvider;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.PackagingDragAndDropSourceItemsProvider;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
* @author nik
*/
public class ModuleOutputElementType extends PackagingElementType<ModuleOutputPackagingElement> {
  public static final ModuleOutputElementType MODULE_OUTPUT_ELEMENT_TYPE = new ModuleOutputElementType();

  ModuleOutputElementType() {
    super("module-output", "Module Output");
  }

  @Override
  public Icon getCreateElementIcon() {
    return IconLoader.getIcon("/nodes/ModuleOpen.png");
  }

  @Override
  public PackagingDragAndDropSourceItemsProvider getDragAndDropSourceItemsProvider() {
    return new ModuleSourceItemsProvider();
  }

  @NotNull
  public List<? extends ModuleOutputPackagingElement> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                       CompositePackagingElement<?> parent) {
    ChooseModulesDialog dialog = new ChooseModulesDialog(context.getProject(), getNotAddedModules(context, artifact), ProjectBundle.message("dialog.title.packaging.choose.module"), "");
    dialog.show();
    List<Module> modules = dialog.getChosenElements();
    final List<ModuleOutputPackagingElement> elements = new ArrayList<ModuleOutputPackagingElement>();
    if (dialog.isOK()) {
      for (Module module : modules) {
        elements.add(new ModuleOutputPackagingElement(module.getName()));
      }
    }
    return elements;
  }

  @NotNull
  public static List<? extends Module> getNotAddedModules(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact) {
    final Set<Module> modules = new HashSet<Module>(Arrays.asList(context.getModulesProvider().getModules()));
    ArtifactUtil.processPackagingElements(artifact, MODULE_OUTPUT_ELEMENT_TYPE, new Processor<ModuleOutputPackagingElement>() {
      public boolean process(ModuleOutputPackagingElement moduleOutputPackagingElement) {
        modules.remove(moduleOutputPackagingElement.findModule(context));
        return true;
      }
    }, context, true);
    return new ArrayList<Module>(modules);
  }

  @NotNull
  public ModuleOutputPackagingElement createEmpty() {
    return new ModuleOutputPackagingElement();
  }
}
