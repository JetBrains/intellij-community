// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ModuleElementsEditor implements ModuleConfigurationEditor {
  protected final @NotNull Project myProject;
  protected JComponent myComponent;
  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private final EventDispatcher<ModuleElementsEditorListener> myDispatcher = EventDispatcher.create(ModuleElementsEditorListener.class);

  private final ModuleConfigurationState myState;

  protected ModuleElementsEditor(@NotNull ModuleConfigurationState state) {
    myProject = state.getProject();
    myState = state;
  }

  public void addListener(ModuleElementsEditorListener listener) {
    myDispatcher.addListener(listener);
  }

  protected void fireConfigurationChanged() {
    myDispatcher.getMulticaster().configurationChanged();
  }

  @Override
  public boolean isModified() {
    return getModel() != null && getModel().isChanged();
  }

  protected ModifiableRootModel getModel() {
    return myState.getModifiableRootModel();
  }

  protected @NotNull ModuleConfigurationState getState() {
    return myState;
  }

  public void canApply() throws ConfigurationException {}

  @Override
  public void apply() throws ConfigurationException {}

  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {}

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposables);
  }

  // caching
  @Override
  public final JComponent createComponent() {
    if (myComponent == null) {
      myComponent = createComponentImpl();
    }
    return myComponent;
  }


  public JComponent getComponent() {
    return createComponent();
  }

  protected void registerDisposable(Disposable disposable) {
    myDisposables.add(disposable);
  }

  protected abstract JComponent createComponentImpl();

  public interface ModuleElementsEditorListener extends EventListener {
    void configurationChanged();
  }
}
