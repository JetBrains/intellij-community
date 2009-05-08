package com.intellij.packaging.impl.ui;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.ColoredTreeCellRenderer;
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

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    if (myModule != null) {
      renderer.setIcon(myModule.getModuleType().getNodeIcon(false));
    }
    renderer.append(CompilerBundle.message("node.text.0.compile.output", myName),
                    myModule != null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @Override
  public double getWeight() {
    return PackagingElementWeights.MODULE;
  }
}
