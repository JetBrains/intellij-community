// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Descriptor {
  private static final Logger LOG = Logger.getInstance(Descriptor.class);

  private final @NotNull @InspectionMessage String myText;
  private final String[] myGroup;
  private final String myShortName;
  private final InspectionToolWrapper<?, ?> myToolWrapper;
  private final HighlightDisplayLevel myLevel;
  private final @Nullable NamedScope myScope;
  private final TextAttributesKey myEditorAttributesKey;
  private final ScopeToolState myState;
  private final @NotNull InspectionProfileModifiableModel myInspectionProfile;

  private Element myConfig;
  private boolean myEnabled;

  public Descriptor(@NotNull ScopeToolState state, @NotNull InspectionProfileModifiableModel inspectionProfile, @NotNull Project project) {
    myState = state;
    myInspectionProfile = inspectionProfile;
    InspectionToolWrapper<?, ?> tool = state.getTool();
    myText = tool.getDisplayName();
    final String[] groupPath = tool.getGroupPath();
    myGroup = groupPath.length == 0 ? new String[]{InspectionProfileEntry.getGeneralGroupName()} : groupPath;
    myShortName = tool.getShortName();
    myScope = state.getScope(project);
    myEditorAttributesKey = state.getEditorAttributesKey();
    final HighlightDisplayKey key = HighlightDisplayKey.findOrRegister(myShortName, myText);
    myLevel = inspectionProfile.getErrorLevel(key, myScope, project);
    myEnabled = inspectionProfile.isToolEnabled(key, myScope, project);
    myToolWrapper = tool;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Descriptor descriptor)) return false;
    return myShortName.equals(descriptor.myShortName) &&
           myLevel.equals(descriptor.getLevel()) &&
           myEnabled == descriptor.isEnabled() &&
           myState.equalTo(descriptor.getState());
  }

  @Override
  public int hashCode() {
    final int hash = myShortName.hashCode() + 29 * myLevel.hashCode();
    return myScope != null ? myScope.hashCode() + 29 * hash : hash;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public @NotNull @InspectionMessage String getText() {
    return myText;
  }

  public @NotNull HighlightDisplayKey getKey() {
    return HighlightDisplayKey.findOrRegister(myShortName, myText);
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public TextAttributesKey getEditorAttributesKey() {
    return myEditorAttributesKey;
  }

  public @Nullable Element getConfig() {
    return myConfig;
  }

  public void loadConfig() {
    if (myConfig == null) {
      InspectionToolWrapper<?, ?> toolWrapper = getToolWrapper();
      myConfig = createConfigElement(toolWrapper);
    }
  }

  public @NotNull InspectionToolWrapper<?, ?> getToolWrapper() {
    return myToolWrapper;
  }

  public @Nullable String loadDescription() {
    loadConfig();
    return myToolWrapper.loadDescription();
  }

  public @NotNull InspectionProfileModifiableModel getInspectionProfile() {
    return myInspectionProfile;
  }

  public static @NotNull Element createConfigElement(InspectionToolWrapper<?, ?> toolWrapper) {
    Element element = new Element("options");
    try {
      toolWrapper.getTool().writeSettings(element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return element;
  }

  public String @NotNull [] getGroup() {
    return myGroup;
  }

  public @NotNull String getScopeName() {
    return myState.getScopeName();
  }

  public @Nullable NamedScope getScope() {
    return myScope;
  }

  public @NotNull ScopeToolState getState() {
    return myState;
  }

  public String getShortName() {
    return myShortName;
  }

  @Override
  public String toString() {
    return myShortName;
  }
}
