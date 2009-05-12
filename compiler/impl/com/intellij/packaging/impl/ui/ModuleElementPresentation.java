package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ModuleElementPresentation extends PackagingElementPresentation {
  private final String myName;
  private final Module myModule;

  public ModuleElementPresentation(String name, Module module) {
    myModule = module;
    myName = name;
  }

  public String getPresentableName() {
    return myName;
  }

  @Override
  public boolean canNavigateToSource() {
    return myModule != null;
  }

  @Override
  public Object getSourceObject() {
    return myModule;
  }

  @Override
  public void navigateToSource() {
    ProjectStructureConfigurable.getInstance(myModule.getProject()).select(myModule.getName(), null, true);
  }

  public void render(@NotNull PresentationData presentationData) {
    if (myModule != null) {
      presentationData.setOpenIcon(myModule.getModuleType().getNodeIcon(true));
      presentationData.setClosedIcon(myModule.getModuleType().getNodeIcon(false));
    }
    presentationData.addText(CompilerBundle.message("node.text.0.compile.output", myName),
                             myModule != null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.MODULE;
  }
}
