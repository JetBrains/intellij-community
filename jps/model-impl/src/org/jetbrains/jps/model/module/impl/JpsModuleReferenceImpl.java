package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceImpl;
import org.jetbrains.jps.model.impl.JpsProjectElementReference;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsModuleReferenceImpl extends JpsNamedElementReferenceImpl<JpsModule, JpsModuleReferenceImpl> implements JpsModuleReference {
  public JpsModuleReferenceImpl(String elementName) {
    super(JpsModuleRole.MODULE_COLLECTION_ROLE, elementName, new JpsProjectElementReference());
  }

  @NotNull
  @Override
  public JpsModuleReferenceImpl createCopy() {
    return new JpsModuleReferenceImpl(myElementName);
  }

  @NotNull
  @Override
  public String getModuleName() {
    return myElementName;
  }

  @Override
  public JpsModuleReference asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }

  @Override
  public String toString() {
    return "module ref: '" + myElementName + "' in " + getParentReference();
  }
}
