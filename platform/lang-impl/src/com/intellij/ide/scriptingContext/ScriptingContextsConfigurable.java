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
import com.intellij.ide.scriptingContext.ui.ScriptingContextsPanel;
import com.intellij.lang.Language;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingContextsConfigurable implements Configurable, Configurable.Composite {

  private MainScriptingContextsPanel myPanel;

  public ScriptingContextsConfigurable() {
    myPanel = new MainScriptingContextsPanel();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Scripting Contexts"; //TODO: Change
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
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void reset() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void disposeUIResources() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public Configurable[] getConfigurables() {
    ArrayList<LangScriptingContextsConfigurable> configurables = new ArrayList<LangScriptingContextsConfigurable>();
    for (LangScriptingContextsProvider provider : LangScriptingContextsProvider.getProviders()) {
      configurables.add(new LangScriptingContextsConfigurable(provider));
    }
    return configurables.toArray(new LangScriptingContextsConfigurable[configurables.size()]);
  }
}
