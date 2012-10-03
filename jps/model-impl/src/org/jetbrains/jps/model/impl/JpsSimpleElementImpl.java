package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author nik
 */
public class JpsSimpleElementImpl<D> extends JpsElementBase<JpsSimpleElementImpl<D>> implements JpsSimpleElement<D> {
  private D myData;

  public JpsSimpleElementImpl(D data) {
    myData = data;
  }

  private JpsSimpleElementImpl(JpsSimpleElementImpl<D> original) {
    myData = original.myData;
  }

  @NotNull
  @Override
  public D getData() {
    return myData;
  }

  @Override
  public void setData(@NotNull D data) {
    if (!myData.equals(data)) {
      myData = data;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public JpsSimpleElementImpl<D> createCopy() {
    return new JpsSimpleElementImpl<D>(this);
  }

  @Override
  public void applyChanges(@NotNull JpsSimpleElementImpl<D> modified) {
    setData(modified.getData());
  }
}
