package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.impl.JpsProjectElementReference;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsModuleReferenceImpl extends JpsNamedElementReferenceBase<JpsModule, JpsModuleReferenceImpl> implements JpsModuleReference {
  public JpsModuleReferenceImpl(JpsModel model, String elementName, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(model, eventDispatcher, JpsModuleKind.MODULE_COLLECTION_KIND, elementName, new JpsProjectElementReference(model, eventDispatcher, parent), parent);
  }

  @NotNull
  @Override
  public JpsModuleReferenceImpl createCopy(@NotNull JpsModel model,
                                           @NotNull JpsEventDispatcher eventDispatcher,
                                           JpsParentElement parent) {
    return new JpsModuleReferenceImpl(model, myElementName, eventDispatcher, parent);
  }

  @NotNull
  @Override
  public String getModuleName() {
    return myElementName;
  }
}
