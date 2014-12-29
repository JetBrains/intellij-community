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

package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Dec 8, 2004
 */
public class Descriptor {
  private final String myText;
  private final String[] myGroup;
  private final HighlightDisplayKey myKey;

  private Element myConfig;
  private final InspectionToolWrapper myToolWrapper;
  private final HighlightDisplayLevel myLevel;
  private boolean myEnabled = false;
  @Nullable
  private final NamedScope myScope;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.Descriptor");
  private final ScopeToolState myState;
  private final InspectionProfileImpl myInspectionProfile;
  private final String myScopeName;

  public Descriptor(@NotNull ScopeToolState state, @NotNull InspectionProfileImpl inspectionProfile, @NotNull Project project) {
    myState = state;
    myInspectionProfile = inspectionProfile;
    InspectionToolWrapper tool = state.getTool();
    myText = tool.getDisplayName();
    final String[] groupPath = tool.getGroupPath();
    myGroup = groupPath.length == 0 ? new String[]{InspectionProfileEntry.GENERAL_GROUP_NAME} : groupPath;
    myKey = HighlightDisplayKey.find(tool.getShortName());
    myScopeName = state.getScopeName();
    myScope = state.getScope(project);
    myLevel = inspectionProfile.getErrorLevel(myKey, myScope, project);
    myEnabled = inspectionProfile.isToolEnabled(myKey, myScope, project);
    myToolWrapper = tool;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Descriptor)) return false;
    final Descriptor descriptor = (Descriptor)obj;
    return myKey.equals(descriptor.getKey()) &&
           myLevel.equals(descriptor.getLevel()) &&
           myEnabled == descriptor.isEnabled() &&
           myState.equalTo(descriptor.getState());
  }

  public int hashCode() {
    final int hash = myKey.hashCode() + 29 * myLevel.hashCode();
    return myScope != null ? myScope.hashCode() + 29 * hash : hash;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getText() {
    return myText;
  }

  @NotNull
  public HighlightDisplayKey getKey() {
    return myKey;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  @Nullable
  public Element getConfig() {
    return myConfig;
  }

  public void loadConfig() {
    if (myConfig == null) {
      InspectionToolWrapper toolWrapper = getToolWrapper();
      myConfig = createConfigElement(toolWrapper);
    }
  }

  @NotNull
  public InspectionToolWrapper getToolWrapper() {
    return myToolWrapper;
  }

  @Nullable
  public String loadDescription() {
    loadConfig();
    return myToolWrapper.loadDescription();
  }

  public InspectionProfileImpl getInspectionProfile() {
    return myInspectionProfile;
  }

  public static Element createConfigElement(InspectionToolWrapper toolWrapper) {
    Element element = new Element("options");
    try {
      toolWrapper.getTool().writeSettings(element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return element;
  }

  public String[] getGroup() {
    return myGroup;
  }

  @NotNull
  public String getScopeName() {
    return myScopeName;
  }

  @Nullable
  public NamedScope getScope() {
    return myScope;
  }

  public ScopeToolState getState() {
    return myState;
  }

  @Override
  public String toString() {
    return myKey.toString();
  }
}
