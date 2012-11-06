package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceDependency;

/**
 * @author nik
 */
public class JpsModuleSourceDependencyImpl extends JpsDependencyElementBase<JpsModuleSourceDependencyImpl>
  implements JpsModuleSourceDependency {
  public JpsModuleSourceDependencyImpl() {
    super();
  }

  public JpsModuleSourceDependencyImpl(JpsModuleSourceDependencyImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsModuleSourceDependencyImpl createCopy() {
    return new JpsModuleSourceDependencyImpl(this);
  }

  @Override
  public String toString() {
    return "module source dep";
  }
}
