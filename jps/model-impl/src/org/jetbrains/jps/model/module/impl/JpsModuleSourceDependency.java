package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;

/**
 * @author nik
 */
public class JpsModuleSourceDependency extends JpsDependencyElementBase<JpsModuleSourceDependency> {
  public JpsModuleSourceDependency(JpsModel model, JpsEventDispatcher eventDispatcher, JpsDependenciesListImpl parent) {
    super(model, eventDispatcher, parent);
  }

  public JpsModuleSourceDependency(JpsModuleSourceDependency original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
  }

  @NotNull
  @Override
  public JpsModuleSourceDependency createCopy(@NotNull JpsModel model,
                                              @NotNull JpsEventDispatcher eventDispatcher,
                                              JpsParentElement parent) {
    return new JpsModuleSourceDependency(this, model, eventDispatcher, parent);
  }
}
