package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.impl.elements.JpsModuleOutputPackagingElementBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsProductionModuleOutputPackagingElementImpl extends JpsModuleOutputPackagingElementBase<JpsProductionModuleOutputPackagingElementImpl>
  implements JpsProductionModuleOutputPackagingElement {
  public JpsProductionModuleOutputPackagingElementImpl(JpsModuleReference moduleReference) {
    super(moduleReference);
  }

  private JpsProductionModuleOutputPackagingElementImpl(JpsProductionModuleOutputPackagingElementImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsProductionModuleOutputPackagingElementImpl createCopy() {
    return new JpsProductionModuleOutputPackagingElementImpl(this);
  }

  @Override
  protected String getOutputUrl(@NotNull JpsModule module) {
    return JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
  }
}
