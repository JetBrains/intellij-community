package org.jetbrains.jps.model.module.impl;

import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.module.JpsDependencyElement;

/**
 * @author nik
 */
public abstract class JpsDependencyElementBase<Self extends JpsDependencyElementBase<Self>> extends JpsCompositeElementBase<Self> implements JpsDependencyElement {
  protected JpsDependencyElementBase() {
    super();
  }

  protected JpsDependencyElementBase(JpsDependencyElementBase<Self> original) {
    super(original);
  }

  @Override
  public void remove() {
    ((JpsDependenciesListImpl)myParent.getParent()).getContainer().getChild(JpsDependenciesListImpl.DEPENDENCY_COLLECTION_ROLE).removeChild(this);
  }

  public JpsDependenciesListImpl getDependenciesList() {
    return (JpsDependenciesListImpl)myParent.getParent();
  }

  @Override
  public JpsModuleImpl getContainingModule() {
    return getDependenciesList().getParent();
  }
}
