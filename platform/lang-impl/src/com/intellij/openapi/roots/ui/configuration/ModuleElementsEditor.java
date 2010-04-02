/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.History;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 4, 2003
 * Time: 7:24:37 PM
 */
public abstract class ModuleElementsEditor implements ModuleConfigurationEditor {
  protected final Project myProject;
  protected JComponent myComponent;
  private final List<Disposable> myDisposables = new ArrayList<Disposable>();

  protected History myHistory;
  private final ModuleConfigurationState myState;

  protected ModuleElementsEditor(ModuleConfigurationState state) {
    myProject = state.getProject();
    myState = state;
  }

  public void setHistory(final History history) {
    myHistory = history;
  }

  public boolean isModified() {
    return getModel() != null && getModel().isChanged();
  }

  protected ModifiableRootModel getModel() {
    return myState.getRootModel();
  }

  protected ModuleConfigurationState getState() {
    return myState;
  }

  public void canApply() throws ConfigurationException {}
  public void apply() throws ConfigurationException {}
  public void reset() {}
  public void moduleStateChanged() {}
  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName){}

  public void disposeUIResources() {
    for (Disposable disposable : myDisposables) {
      Disposer.dispose(disposable);
    }
    myDisposables.clear();
  }

  // caching
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
}
