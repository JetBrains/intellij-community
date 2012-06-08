package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;

/**
 * @author nik
 */
public class JpsModuleSourceDependency extends JpsDependencyElementBase<JpsModuleSourceDependency> {
  public JpsModuleSourceDependency() {
    super();
  }

  public JpsModuleSourceDependency(JpsModuleSourceDependency original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsModuleSourceDependency createCopy() {
    return new JpsModuleSourceDependency(this);
  }
}
