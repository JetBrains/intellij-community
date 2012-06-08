package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsModuleDependencyImpl extends JpsDependencyElementBase<JpsModuleDependencyImpl> implements JpsModuleDependency {
  private static final JpsElementKind<JpsModuleReference> MODULE_REFERENCE_KIND = new JpsElementKind<JpsModuleReference>();

  public JpsModuleDependencyImpl(JpsModel model,
                                 JpsEventDispatcher eventDispatcher,
                                 final JpsModuleReference moduleReference,
                                 JpsDependenciesListImpl parent) {
    super(model, eventDispatcher, parent);
    myContainer.setChild(MODULE_REFERENCE_KIND, moduleReference);
  }

  public JpsModuleDependencyImpl(JpsModuleDependencyImpl original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
  }

  @NotNull
  @Override
  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_KIND);
  }

  @NotNull
  @Override
  public JpsModuleDependencyImpl createCopy(@NotNull JpsModel model,
                                            @NotNull JpsEventDispatcher eventDispatcher,
                                            JpsParentElement parent) {
    return new JpsModuleDependencyImpl(this, model, eventDispatcher, parent);
  }
}
