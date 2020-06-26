// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class ArtifactPropertiesEditors {
  private static final List<String> STANDARD_TABS_ORDER = Arrays.asList(
    ArtifactPropertiesEditor.VALIDATION_TAB, ArtifactPropertiesEditor.PRE_PROCESSING_TAB, ArtifactPropertiesEditor.POST_PROCESSING_TAB
  );
  private final Map<String, JPanel> myMainPanels;
  private final ArtifactEditorContext myContext;
  private final Artifact myOriginalArtifact;
  private final List<PropertiesEditorInfo> myEditors;

  public ArtifactPropertiesEditors(ArtifactEditorContext context, Artifact originalArtifact, Artifact artifact) {
    myContext = context;
    myOriginalArtifact = originalArtifact;
    myMainPanels = new HashMap<>();
    myEditors = new ArrayList<>();
    for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
      final PropertiesEditorInfo editorInfo = new PropertiesEditorInfo(provider);
      myEditors.add(editorInfo);
      final String tabName = editorInfo.myEditor.getTabName();
      JPanel panel = myMainPanels.get(tabName);
      if (panel == null) {
        panel = new JPanel(new VerticalFlowLayout());
        myMainPanels.put(tabName, panel);
      }
      panel.add(editorInfo.myEditor.createComponent());
    }
  }

  public void applyProperties() {
    for (PropertiesEditorInfo editor : myEditors) {
      if (editor.isModified()) {
        editor.applyProperties();
      }
    }
  }

  public void addTabs(TabbedPaneWrapper tabbedPane) {
    List<String> sortedTabs = new ArrayList<>(myMainPanels.keySet());
    sortedTabs.sort((o1, o2) -> {
      int i1 = STANDARD_TABS_ORDER.indexOf(o1);
      if (i1 == -1) i1 = STANDARD_TABS_ORDER.size();
      int i2 = STANDARD_TABS_ORDER.indexOf(o2);
      if (i2 == -1) i2 = STANDARD_TABS_ORDER.size();
      if (i1 != i2) {
        return i1 - i2;
      }
      return o1.compareTo(o2);
    });
    for (String tab : sortedTabs) {
      tabbedPane.addTab(tab, new JBScrollPane(myMainPanels.get(tab)));
    }
  }

  public boolean isModified() {
    for (PropertiesEditorInfo editor : myEditors) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public void removeTabs(TabbedPaneWrapper tabbedPane) {
    for (String tabName : myMainPanels.keySet()) {
      for (int i = 0; i < tabbedPane.getTabCount(); i++) {
        if (tabName.equals(tabbedPane.getTitleAt(i))) {
          tabbedPane.removeTabAt(i);
          break;
        }
      }
    }
  }

  @Nullable
  public String getHelpId(String title) {
    if (ArtifactPropertiesEditor.VALIDATION_TAB.equals(title)) {
      return "reference.project.structure.artifacts.validation";
    }
    else if (ArtifactPropertiesEditor.PRE_PROCESSING_TAB.equals(title)) {
      return "reference.project.structure.artifacts.preprocessing";
    }
    else if (ArtifactPropertiesEditor.POST_PROCESSING_TAB.equals(title)) {
      return "reference.project.structure.artifacts.postprocessing";
    }
    for (PropertiesEditorInfo editorInfo : myEditors) {
      final ArtifactPropertiesEditor editor = editorInfo.myEditor;
      if (editor.getTabName().equals(title)) {
        return editor.getHelpId();
      }
    }
    return null;
  }

  private final class PropertiesEditorInfo {
    private final ArtifactPropertiesEditor myEditor;
    private final ArtifactProperties<?> myProperties;
    private final ArtifactPropertiesProvider myProvider;

    private PropertiesEditorInfo(@NotNull ArtifactPropertiesProvider provider) {
      myProvider = provider;
      myProperties = provider.createProperties(myOriginalArtifact.getArtifactType());
      final ArtifactProperties<?> originalProperties = myOriginalArtifact.getProperties(provider);
      if (originalProperties != null) {
        ArtifactUtil.copyProperties(originalProperties, myProperties);
      }
      myEditor = myProperties.createEditor(myContext);
      myEditor.reset();
    }

    public void applyProperties() {
      myEditor.apply();
      final ModifiableArtifact artifact = myContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact);
      artifact.setProperties(myProvider, myProperties);
    }

    public boolean isModified() {
      return myEditor.isModified();
    }
  }
}
