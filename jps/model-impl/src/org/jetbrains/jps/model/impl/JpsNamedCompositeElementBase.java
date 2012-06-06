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

  protected JpsNamedCompositeElementBase(JpsModel model,
                                         JpsEventDispatcher eventDispatcher,
                                         @NotNull String name, JpsParentElement parent) {
    super(model, eventDispatcher, parent);
    myName = name;
  }

  protected JpsNamedCompositeElementBase(JpsNamedCompositeElementBase<Self, Parent> original,
                                         JpsModel model,
                                         JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(original, model, eventDispatcher, parent);
    myName = original.myName;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void applyChanges(@NotNull Self modified) {
    super.applyChanges(modified);
    setName(((JpsNamedCompositeElementBase)modified).myName);
  }

  @Override
  public void setName(@NotNull String name) {
    if (!myName.equals(name)) {
      String oldName = myName;
      myName = name;
      getEventDispatcher().fireElementRenamed(this, oldName, name);
    }
  }
}
