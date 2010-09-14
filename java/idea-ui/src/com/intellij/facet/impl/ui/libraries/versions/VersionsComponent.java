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
package com.intellij.facet.impl.ui.libraries.versions;

import com.intellij.facet.frameworks.LibrariesDownloadAssistant;
import com.intellij.facet.frameworks.beans.Version;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class VersionsComponent {
  private JPanel myMainPanel;
  private static final String UNKNOWN_RI_NAME = "Unknown";

  private final @NotNull Module myModule;
  private final FacetLibrariesValidator myValidator;

  private final ButtonGroup myButtonGroup = new ButtonGroup();

  private final Map<String, Pair<JRadioButton, JComboBox>> myButtons = new HashMap<String, Pair<JRadioButton, JComboBox>>();

  private Version myCurrentVersion = null;

  public VersionsComponent(@NotNull final Module module, FacetLibrariesValidator validator) {
    myModule = module;
    myValidator = validator;
  }

  public JPanel getJComponent() {
    if (myMainPanel == null) {
      init();
    }
    return myMainPanel;
  }

  @Nullable
  public Version getCurrentVersion() {
    return myCurrentVersion;
  }

  private void init() {
    myMainPanel = new JPanel(new GridBagLayout());

    Set<String> referenceImplementations = getRIs();

    if (referenceImplementations.size() == 1) {
      String ri = referenceImplementations.iterator().next();
      addSingletonReferenceImplementationUI(ri);
    }
    else {
      for (String ri : referenceImplementations) {
        addMultipleReferenceImplementationUI(ri);

        if (myCurrentVersion == null) {
          myCurrentVersion = getCurrentVersion(ri);
        }
      }

      if (myCurrentVersion != null) {
        Pair<JRadioButton, JComboBox> currentPair = myButtons.get(myCurrentVersion.getRI());
        if (currentPair != null) {
          currentPair.first.setSelected(true);
          currentPair.second.setSelectedItem(myCurrentVersion);
          for (Pair<JRadioButton, JComboBox> buttonsPair : myButtons.values()) {
            buttonsPair.second.setEnabled(buttonsPair == currentPair);
          }
        }
      }
    }
  }

  @Nullable
  protected String getFacetDetectionClass(@NotNull String currentRI) {
    return null;
  }

  @NotNull
  protected abstract Version[] getLibraries();

  @Nullable
  private Version getCurrentVersion(@NotNull String currentRI) {
    String detectionClass = getFacetDetectionClass(currentRI);
    if (detectionClass != null) {
      final String version = JarVersionDetectionUtil.detectJarVersion(detectionClass, myModule);
      if (version != null) {
        Version approximatedVersion = null;
        for (Version info : getLibraries()) {
          if (version.equals(info.getId())) {
            return info;
          }
          if (version.contains(info.getId())) {
            approximatedVersion = info;
          }
        }
        return approximatedVersion;
      }
    }

    return null;
  }

  private List<Version> getSupportedVersions(@NotNull String ri) {
    List<Version> versions = new ArrayList<Version>();
    for (Version version : getLibraries()) {
      if (ri.equals(version.getRI())) {
        versions.add(version);
      }
    }

    return versions;
  }

  private void addSingletonReferenceImplementationUI(@NotNull final String ri) {
    JComboBox comboBox = createComboBox(ri);
    addToPanel(new JLabel(ri), comboBox);
    Version version = getCurrentVersion(ri);
    if (version != null) {
      comboBox.setSelectedItem(version);
    }
  }

  private void addMultipleReferenceImplementationUI(@NotNull final String ri) {
    final JRadioButton radioButton = createRadioButton(ri);
    final JComboBox comboBox = createComboBox(ri);

    comboBox.setEnabled(false);

    addToPanel(radioButton, comboBox);

    myButtons.put(ri, new Pair<JRadioButton, JComboBox>(radioButton, comboBox));
    myButtonGroup.add(radioButton);
  }

  private void addToPanel(@NotNull JComponent first, @NotNull JComponent second) {
    myMainPanel.add(first, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.LINE_START,
                                                  GridBagConstraints.BOTH, new Insets(2, 2, 2, 2), 0, 0));
    myMainPanel.add(second,
                    new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.LINE_END, GridBagConstraints.BOTH,
                                           new Insets(2, 2, 2, 2), 0, 0));
  }

  private JRadioButton createRadioButton(final String ri) {
    final JRadioButton radioButton = new JRadioButton(ri);
    radioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        for (Pair<JRadioButton, JComboBox> pair : myButtons.values()) {
          if (pair.getFirst().equals(radioButton)) {
            JComboBox comboBox = pair.second;
            comboBox.setEnabled(true);

            Version currentVersion = getCurrentVersion(ri);
            if (currentVersion != null) {
              comboBox.setSelectedItem(currentVersion);
            }
            else {
              if (comboBox.getSelectedIndex() < 0) {
                comboBox.setSelectedItem(getAppropriateVersion(getSupportedVersions(ri)));
              }
              else {
                updateCurrentVersion(comboBox); // activate already selected
              }
            }
          }
          else {
            pair.second.setEnabled(false);
          }
        }
      }
    });
    return radioButton;
  }

  private JComboBox createComboBox(String ri) {
    final JComboBox comboBox = new JComboBox();

    List<Version> versions = getSupportedVersions(ri);
    comboBox.setModel(new CollectionComboBoxModel(versions, null));

    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateCurrentVersion(comboBox);
      }
    });

    return comboBox;
  }

  private void updateCurrentVersion(JComboBox comboBox) {
    final Version versionInfo = getSelectedVersion(comboBox);

    if (versionInfo != null) {
      myCurrentVersion = versionInfo;
      myValidator.setDescription(getFacetLibrariesValidatorDescription(versionInfo));
      myValidator.setRequiredLibraries(getRequiredLibraries(versionInfo));
    }
  }

  protected FacetLibrariesValidatorDescription getFacetLibrariesValidatorDescription(Version version) {
    return new FacetLibrariesValidatorDescription(version.getId()) {
      @NonNls
      public String getDefaultLibraryName() {
        if (myCurrentVersion != null) {
          String ri = myCurrentVersion.getRI();
          String version = myCurrentVersion.getId();

          return StringUtil.isEmptyOrSpaces(ri) ? version : ri + "." + version;
        }

        return super.getDefaultLibraryName();
      }
    };
  }

  @Nullable
  private static Version getAppropriateVersion(List<Version> versions) {
    return versions.size() > 0 ? versions.get(0) : null;
  }

  private static LibraryInfo[] getRequiredLibraries(Version version) {
    final LibraryInfo[] infos = LibrariesDownloadAssistant.getLibraryInfos(version);

    return infos == null ? LibraryInfo.EMPTY_ARRAY : infos;
  }

  @Nullable
  private static Version getSelectedVersion(@NotNull JComboBox comboBox) {
    final Object version = comboBox.getModel().getSelectedItem();
    return version instanceof Version ? (Version)version : null;
  }


  public FacetLibrariesValidator getValidator() {
    return myValidator;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public Set<String> getRIs() {
    Set<String> ris = new HashSet<String>();
    for (Version version : getLibraries()) {
      String ri = version.getRI();
      if (!StringUtil.isEmptyOrSpaces(ri)) {
        ris.add(ri);
      }
      else {
        ris.add(UNKNOWN_RI_NAME);
      }
    }
    return ris;
  }
}
