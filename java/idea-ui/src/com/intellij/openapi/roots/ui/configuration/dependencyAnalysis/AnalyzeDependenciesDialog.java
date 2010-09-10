/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.dependencyAnalysis;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;

/**
 * The dialog that allows examining dependencies
 */
public class AnalyzeDependenciesDialog extends DialogWrapper {
  /**
   * The analyzer component
   */
  private final AnalyzeDependenciesComponent myComponent;

  /**
   * The constructor
   *
   * @param module the dialog that allows analyzing dependencies
   */
  protected AnalyzeDependenciesDialog(Module module) {
    super(module.getProject(), true);
    setTitle("Analyze Dependencies for " + module.getName());
    setModal(false);
    myComponent = new AnalyzeDependenciesComponent(module);
    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        myComponent.disposeUIResources();
      }
    });
    setOKButtonText("Close");
    init();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myComponent.createComponent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * Show the dialog
   *
   * @param module the module to use
   */
  public static void show(Module module) {
    new AnalyzeDependenciesDialog(module).show();
  }
}
