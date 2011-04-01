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

/*
 * User: anna
 * Date: 20-Apr-2009
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ScopeToolState {
  private NamedScope myScope;
  private final String myScopeName;
  private InspectionProfileEntry myTool;
  private boolean myEnabled;
  private HighlightDisplayLevel myLevel;

  private JComponent myAdditionalConfigPanel;
  private static final Logger LOG = Logger.getInstance("#" + ScopeToolState.class.getName());

  public ScopeToolState(NamedScope scope, @NotNull InspectionProfileEntry tool, boolean enabled, HighlightDisplayLevel level) {
    myScope = scope;
    myScopeName = myScope.getName();
    myTool = tool;
    myEnabled = enabled;
    myLevel = level;
  }

  public ScopeToolState(String scopeName, @NotNull InspectionProfileEntry tool, boolean enabled, HighlightDisplayLevel level) {
    myScopeName = scopeName;
    myTool = tool;
    myEnabled = enabled;
    myLevel = level;
  }

  @Nullable
  public NamedScope getScope() {
    if (myScope == null) {
      final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
      if (project != null) {
        myScope = NamedScopesHolder.getScope(project, myScopeName);
      }
    }
    return myScope;
  }

  @Nullable
  public NamedScope getScope(Project project) {
    if (myScope == null) {
      if (project != null) {
        myScope = NamedScopesHolder.getScope(project, myScopeName);
      }
    }
    return myScope;
  }

  public String getScopeName() {
    return myScopeName;
  }

  @NotNull
  public InspectionProfileEntry getTool() {
    return myTool;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setLevel(HighlightDisplayLevel level) {
    myLevel = level;
  }

  @Nullable
  public JComponent getAdditionalConfigPanel() {
    if (myAdditionalConfigPanel == null){
      myAdditionalConfigPanel = myTool.createOptionsPanel();
      if (myAdditionalConfigPanel == null){
        myAdditionalConfigPanel = new JPanel();
      }
      return myAdditionalConfigPanel;
    }
    return myAdditionalConfigPanel;
  }



  public void resetConfigPanel(){
    myAdditionalConfigPanel = null;
  }

  public void setTool(InspectionProfileEntry tool) {
    myTool = tool;
  }

  public boolean equalTo(ScopeToolState state2) {
    if (isEnabled() != state2.isEnabled()) return false;
    if (getLevel() != state2.getLevel()) return false;
    try {
      @NonNls String tempRoot = "root";
      Element oldToolSettings = new Element(tempRoot);
      getTool().writeSettings(oldToolSettings);
      Element newToolSettings = new Element(tempRoot);
      state2.getTool().writeSettings(newToolSettings);
      return JDOMUtil.areElementsEqual(oldToolSettings, newToolSettings);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return false;
  }
}