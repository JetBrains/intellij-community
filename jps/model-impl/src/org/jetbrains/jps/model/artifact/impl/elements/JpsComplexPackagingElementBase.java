package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.jps.model.artifact.elements.JpsComplexPackagingElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;

/**
 * @author nik
 */
public abstract class JpsComplexPackagingElementBase<Self extends JpsComplexPackagingElementBase<Self>> extends JpsCompositeElementBase<Self> implements
                                                                                                                                              JpsComplexPackagingElement {
  protected JpsComplexPackagingElementBase() {
  }

  protected JpsComplexPackagingElementBase(JpsComplexPackagingElementBase<Self> original) {
    super(original);
  }
}
