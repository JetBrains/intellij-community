package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public abstract class JpsModuleOutputPackagingElementBase<Self extends JpsModuleOutputPackagingElementBase<Self>> extends JpsCompositeElementBase<Self> implements
                                                                                                                                                        JpsModuleOutputPackagingElement {
  private static final JpsElementKind<JpsModuleReference> MODULE_REFERENCE_KIND = JpsElementKindBase.create("module reference");

  public JpsModuleOutputPackagingElementBase(JpsModuleReference moduleReference) {
    myContainer.setChild(MODULE_REFERENCE_KIND, moduleReference);
  }

  public JpsModuleOutputPackagingElementBase(JpsModuleOutputPackagingElementBase<Self> original) {
    super(original);
  }

  @Override
  @NotNull
  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_KIND);
  }

  @Override
  @Nullable
  public String getOutputUrl() {
    JpsModule module = getModuleReference().resolve();
    if (module == null) return null;
    return getOutputUrl(module);
  }

  @Nullable
  protected abstract String getOutputUrl(@NotNull JpsModule module);
}
