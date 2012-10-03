package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

/**
 * @author nik
 */
public class JpsJavaDependencyExtensionImpl extends JpsElementBase<JpsJavaDependencyExtensionImpl> implements JpsJavaDependencyExtension {
  private boolean myExported;
  private JpsJavaDependencyScope myScope;

  public JpsJavaDependencyExtensionImpl(boolean exported,
                                        JpsJavaDependencyScope scope) {
    myExported = exported;
    myScope = scope;
  }

  public JpsJavaDependencyExtensionImpl(JpsJavaDependencyExtensionImpl original) {
    myExported = original.myExported;
    myScope = original.myScope;
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean exported) {
    if (myExported != exported) {
      myExported = exported;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public JpsJavaDependencyScope getScope() {
    return myScope;
  }

  @Override
  public void setScope(@NotNull JpsJavaDependencyScope scope) {
    if (!scope.equals(myScope)) {
      myScope = scope;
      fireElementChanged();
    }
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl createCopy() {
    return new JpsJavaDependencyExtensionImpl(this);
  }

  @Override
  public void applyChanges(@NotNull JpsJavaDependencyExtensionImpl modified) {
    setExported(modified.myExported);
    setScope(modified.myScope);
  }
}
