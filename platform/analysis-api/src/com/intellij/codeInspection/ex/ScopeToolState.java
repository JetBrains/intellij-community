// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ui.OptionPaneRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public final class ScopeToolState {
  private static final Logger LOG = Logger.getInstance(ScopeToolState.class);
  private final @NotNull String myScopeName;
  private NamedScope myScope;
  private InspectionToolWrapper<?, ?> myToolWrapper;
  private boolean myEnabled;
  private HighlightDisplayLevel myLevel;
  private String myEditorAttributesKey;
  private ConfigPanelState myAdditionalConfigPanelState;

  public ScopeToolState(@NotNull NamedScope scope,
                        @NotNull InspectionToolWrapper<?, ?> toolWrapper,
                        boolean enabled,
                        @NotNull HighlightDisplayLevel level) {
    this(scope.getScopeId(), toolWrapper, enabled, level);
    myScope = scope;
  }

  public ScopeToolState(@NotNull String scopeName,
                        @NotNull InspectionToolWrapper<?, ?> toolWrapper,
                        boolean enabled,
                        @NotNull HighlightDisplayLevel level) {
    myScopeName = scopeName;
    myToolWrapper = toolWrapper;
    myEnabled = enabled;
    myLevel = level;
  }

  public @NotNull ScopeToolState copy() {
    return new ScopeToolState(myScopeName, myToolWrapper, myEnabled, myLevel);
  }

  public @Nullable NamedScope getScope(@Nullable Project project) {
    if (myScope == null && project != null) {
      myScope = NamedScopesHolder.getScope(project, myScopeName);
    }
    return myScope;
  }

  public @NotNull String getScopeName() {
    return myScopeName;
  }

  public @NotNull InspectionToolWrapper<?, ?> getTool() {
    return myToolWrapper;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public @NotNull HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setLevel(@NotNull HighlightDisplayLevel level) {
    myLevel = level;
  }

  public @Nullable TextAttributesKey getEditorAttributesKey() {
    if (myEditorAttributesKey != null) {
      return TextAttributesKey.find(myEditorAttributesKey);
    }
    final String externalName = myToolWrapper.getDefaultEditorAttributes();
    return externalName == null ? null : TextAttributesKey.find(externalName);
  }

  public @Nullable String getEditorAttributesExternalName() {
    return myEditorAttributesKey;
  }

  public void setEditorAttributesExternalName(@Nullable String textAttributesKey) {
    myEditorAttributesKey = textAttributesKey;
  }

  public @Nullable JComponent getAdditionalConfigPanel(@NotNull Disposable parent, @NotNull Project project) {
    if (myAdditionalConfigPanelState == null) {
      myAdditionalConfigPanelState = ConfigPanelState.of(
        OptionPaneRenderer.createOptionsPanel(myToolWrapper.getTool(), parent, project), myToolWrapper);
    }
    return myAdditionalConfigPanelState.getPanel(isEnabled());
  }

  public void resetConfigPanel() {
    myAdditionalConfigPanelState = null;
  }

  public void setTool(@NotNull InspectionToolWrapper<?, ?> tool) {
    myToolWrapper = tool;
  }

  public boolean equalTo(@NotNull ScopeToolState state2) {
    if (isEnabled() != state2.isEnabled()) return false;
    if (getLevel() != state2.getLevel()) return false;
    if (!Objects.equals(getEditorAttributesExternalName(), state2.getEditorAttributesExternalName())) return false;
    InspectionToolWrapper<?, ?> toolWrapper = getTool();
    InspectionToolWrapper<?, ?> toolWrapper2 = state2.getTool();
    if (!toolWrapper.isInitialized() && !toolWrapper2.isInitialized()) return true;
    return areSettingsEqual(toolWrapper, toolWrapper2);
  }

  public static boolean areSettingsEqual(@NotNull InspectionToolWrapper<?, ?> toolWrapper, @NotNull InspectionToolWrapper<?, ?> toolWrapper2) {
    try {
      @NonNls String tempRoot = "root";
      Element oldToolSettings = new Element(tempRoot);
      tryWriteSettings(toolWrapper.getTool(), oldToolSettings);
      Element newToolSettings = new Element(tempRoot);
      tryWriteSettings(toolWrapper2.getTool(), newToolSettings);
      return JDOMUtil.areElementsEqual(oldToolSettings, newToolSettings);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return false;
  }

  public void scopesChanged() {
    myScope = null;
  }

  public static void tryReadSettings(@NotNull InspectionProfileEntry entry, @NotNull Element node) throws InvalidDataException {
    try {
      entry.readSettings(node);
    }
    catch (InvalidDataException | ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new InvalidDataException("Can't read settings for tool #" + entry.getShortName(), e);
    }
  }

  public static void tryWriteSettings(@NotNull InspectionProfileEntry entry, @NotNull Element node) throws WriteExternalException {
    try {
      entry.writeSettings(node);
    }
    catch (WriteExternalException | ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException("Can't write settings for tool #" + entry.getShortName(), e);
    }
  }

  private static final class ConfigPanelState {
    private static final ConfigPanelState EMPTY = new ConfigPanelState(null, null);

    private final JComponent myOptionsPanel;
    private final Set<Component> myEnableRequiredComponent = new HashSet<>();

    private boolean myLastState = true;
    private boolean myDeafListeners;

    private ConfigPanelState(JComponent optionsPanel, InspectionToolWrapper<?, ?> wrapper) {
      myOptionsPanel = optionsPanel;
      if (myOptionsPanel != null) {
        Deque<Component> q = new ArrayDeque<>(1);
        q.addLast(optionsPanel);
        while (!q.isEmpty()) {
          Component current = q.removeFirst();
          current.addPropertyChangeListener("enabled", evt -> {
            if (!myDeafListeners) {
              final boolean newValue = (boolean)evt.getNewValue();
              if (newValue) {
                myEnableRequiredComponent.add(current);
              }
              else {
                String message = wrapper == null
                                 ? null
                                 : (" tool = #" + wrapper.getShortName());
                LOG.assertTrue(myEnableRequiredComponent.remove(current), message);
              }
            }
          });
          if (current.isEnabled()) {
            myEnableRequiredComponent.add(current);
          }
          if (current instanceof Container) {
            for (Component child : ((Container)current).getComponents()) {
              q.addLast(child);
            }
          }
        }
      }
    }

    private JComponent getPanel(boolean currentState) {
      if (myOptionsPanel != null) {
        if (myLastState != currentState) {
          myDeafListeners = true;
          try {
            for (Component c : myEnableRequiredComponent) {
              c.setEnabled(currentState);
            }
            myLastState = currentState;
          }
          finally {
            myDeafListeners = false;
          }
        }
      }
      return myOptionsPanel;
    }

    private static ConfigPanelState of(JComponent panel, InspectionToolWrapper<?, ?> toolWrapper) {
      return panel == null ? EMPTY : new ConfigPanelState(panel, toolWrapper);
    }
  }
}