package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

/**
 * @author nik
 */
public class JpsJavaDependencyExtensionImpl extends JpsElementBase<JpsJavaDependencyExtensionImpl> implements JpsJavaDependencyExtension {
  private boolean myExported;
  private JpsJavaDependencyScope myScope;

  public JpsJavaDependencyExtensionImpl(JpsEventDispatcher eventDispatcher, JpsParentElement parent, boolean exported, JpsJavaDependencyScope scope) {
    super(eventDispatcher, parent);
    myExported = exported;
    myScope = scope;
  }

  public JpsJavaDependencyExtensionImpl(JpsJavaDependencyExtensionImpl original, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(original, eventDispatcher, parent);
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
      getEventDispatcher().fireElementChanged(this);
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
      getEventDispatcher().fireElementChanged(this);
    }
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl createCopy(@NotNull JpsModel model,
                                                   @NotNull JpsEventDispatcher eventDispatcher,
                                                   JpsParentElement parent) {
    return new JpsJavaDependencyExtensionImpl(this, eventDispatcher, parent);
  }

  @Override
  public void applyChanges(@NotNull JpsJavaDependencyExtensionImpl modified) {
    setExported(modified.myExported);
    setScope(modified.myScope);
  }
}
