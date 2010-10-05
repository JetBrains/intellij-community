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

import com.intellij.ide.scriptingContext.ui.MainScriptingContextsPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingContextsConfigurable implements Configurable, Configurable.Composite {

  private MainScriptingContextsPanel myPanel;
  private String myLangNames;
  private Project myProject;
  private ModifiableRootModel myRootModel;

  public ScriptingContextsConfigurable(Project project) {
    myPanel = new MainScriptingContextsPanel();
    myLangNames = getLangNames();
    myProject = project;
    myRootModel = ScriptingLibraryManager.getRootModel(project);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Scripting Libraries";
  }

  @Override
  public Icon getIcon() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getHelpTopic() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public JComponent createComponent() {
    return myPanel.getTopPanel();
  }

  @Override
  public boolean isModified() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myRootModel != null) {
      myRootModel.commit();
    }
  }

  @Override
  public void reset() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void disposeUIResources() {
    disposeModel();
  }

  @Override
  public Configurable[] getConfigurables() {
    ArrayList<LangScriptingContextConfigurable> configurables = new ArrayList<LangScriptingContextConfigurable>();
    for (LangScriptingContextProvider provider : LangScriptingContextProvider.getProviders()) {
      configurables.add(new LangScriptingContextConfigurable(myRootModel, provider));
    }
    return configurables.toArray(new LangScriptingContextConfigurable[configurables.size()]);
  }

  private static String getLangNames() {
    StringBuffer buf = new StringBuffer();
    for (LangScriptingContextProvider provider : LangScriptingContextProvider.getProviders()) {
      buf.append(provider.getLanguage().getDisplayName()).append('/');
    }
    String result = buf.toString();
    return result.substring(0, result.length() - 1);
  }

  public void disposeModel() {
    if (myRootModel != null && !myRootModel.isDisposed()) {
      myRootModel.dispose();
    }
  }
}
