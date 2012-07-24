package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.impl.elements.JpsModuleOutputPackagingElementBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsTestModuleOutputPackagingElementImpl extends JpsModuleOutputPackagingElementBase<JpsTestModuleOutputPackagingElementImpl>
  implements JpsTestModuleOutputPackagingElement {
  public JpsTestModuleOutputPackagingElementImpl(JpsModuleReference moduleReference) {
    super(moduleReference);
  }

  private JpsTestModuleOutputPackagingElementImpl(JpsTestModuleOutputPackagingElementImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsTestModuleOutputPackagingElementImpl createCopy() {
    return new JpsTestModuleOutputPackagingElementImpl(this);
  }

  @Override
  protected String getOutputUrl(@NotNull JpsModule module) {
    return JpsJavaExtensionService.getInstance().getOutputUrl(module, true);
  }
}
