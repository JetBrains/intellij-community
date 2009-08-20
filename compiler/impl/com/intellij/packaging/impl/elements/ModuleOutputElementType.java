package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
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
    super("module-output", CompilerBundle.message("element.type.name.module.output"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return IconLoader.getIcon("/nodes/ModuleOpen.png");
  }

  @NotNull
  public List<? extends ModuleOutputPackagingElement> createWithDialog(@NotNull PackagingEditorContext context, Artifact artifact,
                                                                       CompositePackagingElement<?> parent) {
    List<Module> modules = chooseModules(context, artifact);
    final List<ModuleOutputPackagingElement> elements = new ArrayList<ModuleOutputPackagingElement>();
    for (Module module : modules) {
      elements.add(new ModuleOutputPackagingElement(module.getName()));
    }
    return elements;
  }

  public static List<Module> chooseModules(PackagingEditorContext context, Artifact artifact) {
    ChooseModulesDialog dialog = new ChooseModulesDialog(context.getProject(), getNotAddedModules(context, artifact, context.getModulesProvider().getModules()), ProjectBundle.message("dialog.title.packaging.choose.module"), "");
    dialog.show();
    List<Module> modules = dialog.getChosenElements();
    if (!dialog.isOK()) {
      modules = Collections.emptyList();
    }
    return modules;
  }

  @NotNull
  public static List<? extends Module> getNotAddedModules(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact,
                                                          final Module... allModules) {
    final Set<Module> modules = new HashSet<Module>(Arrays.asList(allModules));
    ArtifactUtil.processPackagingElements(artifact, MODULE_OUTPUT_ELEMENT_TYPE, new Processor<ModuleOutputPackagingElement>() {
      public boolean process(ModuleOutputPackagingElement moduleOutputPackagingElement) {
        modules.remove(moduleOutputPackagingElement.findModule(context));
        return true;
      }
    }, context, true);
    return new ArrayList<Module>(modules);
  }

  @NotNull
  public ModuleOutputPackagingElement createEmpty(@NotNull Project project) {
    return new ModuleOutputPackagingElement();
  }
}
