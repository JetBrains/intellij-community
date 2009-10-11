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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import com.intellij.openapi.project.ProjectBundle;

import javax.swing.*;

/**
 * @author nik
*/
public class RemoteRepositoryMirrorPanel {
  private JPanel myPanel;
  private JComboBox myMirrorComboBox;
  private JLabel myFromLabel;
  private final RemoteRepositoryInfo myRemoteRepository;

  public RemoteRepositoryMirrorPanel(final RemoteRepositoryInfo remoteRepository, final LibraryDownloadingMirrorsMap mirrorsMap) {
    myRemoteRepository = remoteRepository;
    myFromLabel.setText(ProjectBundle.message("composing.library.from.repository.label", remoteRepository.getPresentableName()));
    updateComboBox(mirrorsMap);
  }

  public void updateComboBox(final LibraryDownloadingMirrorsMap mirrorsMap) {
    myMirrorComboBox.removeAllItems();
    for (String mirror : myRemoteRepository.getMirrors()) {
      myMirrorComboBox.addItem(mirror);
    }
    myMirrorComboBox.setSelectedItem(mirrorsMap.getSelectedMirror(myRemoteRepository));
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public RemoteRepositoryInfo getRemoteRepository() {
    return myRemoteRepository;
  }

  public String getSelectedMirror() {
    return (String)myMirrorComboBox.getSelectedItem();
  }
}
