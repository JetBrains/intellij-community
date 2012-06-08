package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public abstract class JpsNamedCompositeElementBase<Self extends JpsNamedCompositeElementBase<Self, Parent>, Parent extends JpsCompositeElementBase> extends
                                                                                                                                                    JpsCompositeElementBase<Self>
  implements JpsNamedElement {
  private String myName;

  protected JpsNamedCompositeElementBase(@NotNull String name) {
    super();
    myName = name;
  }

  protected JpsNamedCompositeElementBase(JpsNamedCompositeElementBase<Self, Parent> original) {
    super(original);
    myName = original.myName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void applyChanges(@NotNull Self modified) {
    super.applyChanges(modified);
    setName(modified.getName());
  }

  @Override
  public void setName(@NotNull String name) {
    if (!myName.equals(name)) {
      String oldName = myName;
      myName = name;
      final JpsEventDispatcher eventDispatcher = getEventDispatcher();
      if (eventDispatcher != null) {
        eventDispatcher.fireElementRenamed(this, oldName, name);
      }
    }
  }
}
