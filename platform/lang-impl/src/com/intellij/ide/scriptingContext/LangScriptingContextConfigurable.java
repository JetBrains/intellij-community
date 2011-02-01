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
package com.intellij.ide.scriptingContext;

import com.intellij.ide.scriptingContext.ui.ScriptingLibrariesPanelStub;
import com.intellij.ide.scriptingContext.ui.ScriptingContextsConfigurable;
import com.intellij.ide.scriptingContext.ui.ScriptingLibrariesPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public abstract class LangScriptingContextConfigurable implements Configurable, Configurable.Composite {
  private final ScriptingLibrariesPanelStub myPanel;
  private final ScriptingLibraryManager myLibManager;
  private ScriptingContextsConfigurable myContextsConfigurable;

  public LangScriptingContextConfigurable(Project project, LangScriptingContextProvider provider) {
    myLibManager = new ScriptingLibraryManager(project, provider.getLibraryType());
    myPanel = useDedicatedLibraryUI(project)
              ? new ScriptingLibrariesPanel(provider, project, myLibManager)
              : new ScriptingLibrariesPanelStub(project);
    myContextsConfigurable = new ScriptingContextsConfigurable(this, project, provider.getLibraryMappings(project));
  }

  private static boolean useDedicatedLibraryUI(Project project) {
    return project.getPicoContainer().getComponentInstance("com.intellij.openapi.roots.ui.configuration.projectRoot.GlobalLibrariesConfigurable") == null;
  }

  @Override
  public JComponent createComponent() {
    return myPanel.getPanel();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myLibManager.commitChanges();
        myPanel.resetTable();
        myContextsConfigurable.reset();
      }
    });
  }

  @Override
  public void reset() {
    myLibManager.reset();
    myPanel.resetTable();
  }

  @Override
  public void disposeUIResources() {

  }

  @Override
  public Configurable[] getConfigurables() {
    return new Configurable[] {myContextsConfigurable};
  }
  
  @Nullable
  public abstract String getUsageScopeHelpTopic();
}
