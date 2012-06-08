package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public class JpsModelImpl implements JpsModel {
  private JpsProjectImpl myProject;
  private JpsGlobalImpl myGlobal;
  private JpsModelImpl myOriginalModel;
  private final JpsEventDispatcher myEventDispatcher;

  public JpsModelImpl(JpsEventDispatcher eventDispatcher) {
    myEventDispatcher = eventDispatcher;
    myProject = new JpsProjectImpl(this, eventDispatcher);
    myGlobal = new JpsGlobalImpl(this, eventDispatcher);
  }

  private JpsModelImpl(JpsModelImpl original, JpsEventDispatcher eventDispatcher) {
    myOriginalModel = original;
    myEventDispatcher = eventDispatcher;
    myProject = new JpsProjectImpl(original.myProject, this, eventDispatcher);
    myGlobal = new JpsGlobalImpl(original.myGlobal, this, eventDispatcher);
  }

  @NotNull
  public JpsProjectImpl getProject() {
    return myProject;
  }

  @NotNull
  public JpsGlobalImpl getGlobal() {
    return myGlobal;
  }

  @NotNull
  @Override
  public JpsModel createModifiableModel(@NotNull JpsEventDispatcher eventDispatcher) {
    return new JpsModelImpl(this, eventDispatcher);
  }

  @Override
  public void registerExternalReference(@NotNull JpsElementReference<?> reference) {
    myProject.addExternalReference(reference);
  }

  @Override
  public void commit() {
    myOriginalModel.applyChanges(this);
  }

  private void applyChanges(@NotNull JpsModelImpl modifiedCopy) {
    myProject.applyChanges(modifiedCopy.myProject);
    myGlobal.applyChanges(modifiedCopy.myGlobal);
  }
}
