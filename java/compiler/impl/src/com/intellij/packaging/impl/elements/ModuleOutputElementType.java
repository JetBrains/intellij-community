package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return context.getModulesProvider().getModules().length > 0;
  }

  @NotNull
  public List<? extends ModuleOutputPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                       @NotNull CompositePackagingElement<?> parent) {
    List<Module> modules = chooseModules(context);
    final List<ModuleOutputPackagingElement> elements = new ArrayList<ModuleOutputPackagingElement>();
    for (Module module : modules) {
      elements.add(new ModuleOutputPackagingElement(module.getName()));
    }
    return elements;
  }

  public static List<Module> chooseModules(ArtifactEditorContext context) {
    return context.chooseModules(Arrays.asList(context.getModulesProvider().getModules()), ProjectBundle.message("dialog.title.packaging.choose.module"));
  }

  @NotNull
  public ModuleOutputPackagingElement createEmpty(@NotNull Project project) {
    return new ModuleOutputPackagingElement();
  }
}
