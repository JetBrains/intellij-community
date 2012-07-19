package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsModuleDependencyImpl extends JpsDependencyElementBase<JpsModuleDependencyImpl> implements JpsModuleDependency {
  private static final JpsElementKind<JpsModuleReference> MODULE_REFERENCE_KIND = new JpsElementKindBase<JpsModuleReference>("module reference");

  public JpsModuleDependencyImpl(final JpsModuleReference moduleReference) {
    super();
    myContainer.setChild(MODULE_REFERENCE_KIND, moduleReference);
  }

  public JpsModuleDependencyImpl(JpsModuleDependencyImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_KIND);
  }

  @Override
  public JpsModule getModule() {
    return getModuleReference().resolve();
  }

  @NotNull
  @Override
  public JpsModuleDependencyImpl createCopy() {
    return new JpsModuleDependencyImpl(this);
  }
}
