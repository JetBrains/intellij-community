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
