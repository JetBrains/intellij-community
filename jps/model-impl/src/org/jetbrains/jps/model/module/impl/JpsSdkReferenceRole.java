package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

public class JpsSdkReferenceRole<P extends JpsElement> extends JpsElementChildRoleBase<JpsSdkReference<P>> {
  private final JpsSdkType<P> myType;

  public JpsSdkReferenceRole(@NotNull JpsSdkType<P> type) {
    super("sdk reference " + type);
    myType = type;
  }

  @Override
  public int hashCode() {
    return myType.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof JpsSdkReferenceRole && myType.equals(((JpsSdkReferenceRole)obj).myType);
  }
}
