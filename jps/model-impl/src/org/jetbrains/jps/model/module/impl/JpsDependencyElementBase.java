package org.jetbrains.jps.model.module.impl;

import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.module.JpsDependencyElement;

/**
 * @author nik
 */
public abstract class JpsDependencyElementBase<Self extends JpsDependencyElementBase<Self>> extends JpsCompositeElementBase<Self> implements JpsDependencyElement {
  protected JpsDependencyElementBase(JpsModel model, JpsEventDispatcher eventDispatcher, JpsDependenciesListImpl parent) {
    super(model, eventDispatcher, parent);
  }

  protected JpsDependencyElementBase(JpsDependencyElementBase<Self> original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
  }

  @Override
  public void remove() {
    ((JpsDependenciesListImpl)myParent).getContainer().getChild(JpsDependenciesListImpl.DEPENDENCY_COLLECTION_KIND).removeChild(this);
  }
}
