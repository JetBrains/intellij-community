/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 31-Jul-2006
 * Time: 17:44:39
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public abstract class InspectionToolsConfigurable extends BaseConfigurable implements ErrorsConfigurable {
  private CardLayout myLayout = new CardLayout();
  private JPanel myPanel;

  public static final String ID = "Errors";
  public static final String DISPLAY_NAME = "Inspections";

  protected JComboBox myProfiles;
  private Map<String, SingleInspectionProfilePanel> myPanels = new HashMap<String, SingleInspectionProfilePanel>();

  private JPanel myWholePanel;
  private JButton myAddButton;
  private JButton myDeleteButton;
  private JButton myImportButton;
  private JButton myExportButton;
  protected JButton myActivateButton;
  private InspectionProfileImpl myActiveProfile = null;

  private ArrayList<String> myDeletedProfiles = new ArrayList<String>();
  protected final InspectionProfileManager myProfileManager;
  protected final InspectionProjectProfileManager myProjectProfileManager;
  private static final Logger LOG = Logger.getInstance("#" + InspectionToolsConfigurable.class.getName());
  private static final Icon DEFAULT_PROJECT_PROFILE = IconLoader.getIcon("/ide/defaultProjectProfile.png");


  public InspectionToolsConfigurable(InspectionProjectProfileManager projectProfileManager, InspectionProfileManager profileManager) {
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ModifiableModel model = SingleInspectionProfilePanel.createNewProfile(-1, getSelectedObject(), myWholePanel, "");
        if (model != null) {
          addProfile((InspectionProfileImpl)model);
          myDeletedProfiles.remove(model.getName());
          myDeleteButton.setEnabled(true);
        }
      }
    });

    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl selectedProfile = (InspectionProfileImpl)myProfiles.getSelectedItem();
        ((DefaultComboBoxModel)myProfiles.getModel()).removeElement(selectedProfile);
        myDeletedProfiles.add(selectedProfile.getName());
        myDeleteButton.setEnabled(myProfiles.getModel().getSize() > 1);
      }
    });

    myImportButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false){
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return file.getFileType().equals(StdFileTypes.XML);
          }
        };
        descriptor.setDescription("Choose profile file");
        final VirtualFile[] files = FileChooser.chooseFiles(myWholePanel, descriptor);
        if (files.length == 0) return;
        InspectionProfileImpl inspectionProfile =
        new InspectionProfileImpl("TempProfile", InspectionToolRegistrar.getInstance(), myProfileManager);
        inspectionProfile.initInspectionTools();
        final InspectionProfileImpl profile = (InspectionProfileImpl)inspectionProfile.getModifiableModel();
        try {
          profile.readExternal(JDOMUtil.loadDocument(VfsUtil.virtualToIoFile(files[0])).getRootElement());

          if (myProfileManager.getProfile(profile.getName(), false) != null) {
            if (Messages.showOkCancelDialog(myWholePanel, "Profile with name \'" + profile.getName() + "\' already exists. Do you want to overwrite it?", "Warning", Messages.getInformationIcon()) != DialogWrapper.OK_EXIT_CODE) return;
          }
          addProfile(profile);
          myDeletedProfiles.remove(profile.getName());
          myDeleteButton.setEnabled(true);
        }
        catch (InvalidDataException e1) {
          LOG.error(e1);
        }
        catch (JDOMException e1) {
          LOG.error(e1);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });

    myExportButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setDescription("Choose directory to store profile file");
        final VirtualFile[] files = FileChooser.chooseFiles(myWholePanel, descriptor);
        if (files.length == 0) return;
        final Element element = new Element("inspections");
        try {
          final InspectionProfileImpl profile = (InspectionProfileImpl)myProfiles.getSelectedItem();
          profile.writeExternal(element);
          final String filePath = files[0].getPath() + File.separator + FileUtil.sanitizeFileName(profile.getName()) + ".xml";
          if (new File(filePath).isFile()) {
            if (Messages.showOkCancelDialog(myWholePanel, "File \'" + filePath + "\' already exist. Do you want to override it?", "Warning", Messages.getQuestionIcon()) != DialogWrapper.OK_EXIT_CODE) return;
          }
          JDOMUtil.writeDocument(new Document(element), filePath, SystemProperties.getLineSeparator());
        }
        catch (WriteExternalException e1) {
          LOG.error(e1);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });

    myActivateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myActiveProfile = (InspectionProfileImpl)myProfiles.getSelectedItem();
        myActivateButton.setEnabled(false);
        final SingleInspectionProfilePanel panel = getSelectedPanel();
        if (panel != null) {
          panel.setSharedEnabled(false);
        }
      }
    });

    myProjectProfileManager = projectProfileManager;
    myProfileManager = profileManager;
  }

  private void addProfile(InspectionProfileImpl model) {
    final String modelName = model.getName();
    final SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(myProjectProfileManager, modelName, model);
    addSharedProfileListener(panel);
    myPanel.add(modelName, panel);
    if (!myPanels.containsKey(modelName)) {
      ((DefaultComboBoxModel)myProfiles.getModel()).addElement(model);
    }
    myPanels.put(modelName, panel);
    myProfiles.setSelectedItem(model);
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableErrorHighlighting.png");
  }

  public String getHelpTopic() {
    return "preferences.errorHighlight";
  }

  public JComponent createComponent() {
    myProfiles.setRenderer(new DefaultListCellRenderer(){
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final String profileName = ((Profile)value).getName();
        setText(profileName);
        final SingleInspectionProfilePanel panel = myPanels.get(profileName);
        if (panel != null && panel.isProfileShared()) {
          if (value == myActiveProfile) {
            setIcon(DEFAULT_PROJECT_PROFILE);
          }
          else {
            setIcon(Profile.PROJECT_PROFILE);
          }
        } else {
          setIcon(Profile.LOCAL_PROFILE);
        }
        return rendererComponent;
      }
    });
    myProfiles.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl profile = (InspectionProfileImpl)myProfiles.getSelectedItem();
        final SingleInspectionProfilePanel panel = getSelectedPanel();
        myDeleteButton.setEnabled(profile != myActiveProfile);
        if (panel != null) {
          myActivateButton.setEnabled(profile != myActiveProfile);
          panel.setSharedEnabled(profile != myActiveProfile);
        }
        myLayout.show(myPanel, profile.getName());
      }
    });

    myPanel.setLayout(myLayout);
    return myWholePanel;
  }

  protected abstract InspectionProfileImpl getCurrentProfile();

  public boolean isModified() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      if (panel.isModified()) return true;
    }
    if (getProfiles().size() != myPanels.size()) return true;
    return !myDeletedProfiles.isEmpty();
  }

  public void apply() throws ConfigurationException {
    for (String name : myPanels.keySet()) {
      if (myDeletedProfiles.contains(name)) {
        deleteProfile(name);
      } else {
        myPanels.get(name).apply();
      }
    }
    myDeletedProfiles.clear();
    myPanels.clear();
  }

  protected void deleteProfile(String name) {
    if (myProfileManager.getProfile(name, false) != null) {
      myProfileManager.deleteProfile(name);
    }
    if (myProjectProfileManager.getProfile(name, false) != null) {
      myProjectProfileManager.deleteProfile(name);
    }
  }

  protected InspectionProfileImpl getActiveProfile() {
    return myActiveProfile;
  }

  public void reset() {
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    myProfiles.setModel(model);
    for (Profile profile : getProfiles()) {
      model.addElement(profile);
      final String profileName = profile.getName();
      final SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(myProjectProfileManager, profileName, ((InspectionProfileImpl)profile).getModifiableModel());
      addSharedProfileListener(panel);
      myPanels.put(profileName, panel);
      panel.reset();
      myPanel.add(profileName, panel);
    }
    final InspectionProfileImpl inspectionProfile = getCurrentProfile();
    myProfiles.setSelectedItem(inspectionProfile);
    myLayout.show(myPanel, inspectionProfile.getName());
    myDeleteButton.setEnabled(getProfiles().size() > 1 && inspectionProfile.getProfileManager() == myProfileManager);
    myActiveProfile = inspectionProfile;
    myActivateButton.setEnabled(false);
    getSelectedPanel().setSharedEnabled(inspectionProfile.getProfileManager() == myProfileManager);
  }

  private void addSharedProfileListener(final SingleInspectionProfilePanel panel) {
    panel.addSharedProfileListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myActivateButton.setEnabled(myActiveProfile != panel.getSelectedProfile() && panel.isProfileShared());
        myProfiles.repaint();
      }
    });
  }

  protected Collection<Profile> getProfiles() {
    final Collection<Profile> result = new ArrayList<Profile>();
    result.addAll(myProfileManager.getProfiles());
    result.addAll(myProjectProfileManager.getProfiles());
    return result;
  }

  public void disposeUIResources() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      panel.disposeUI();
    }
    myPanels.clear();
    myActiveProfile = null;
  }

  public void selectProfile(String name) {
    for (int i = 0; i < myProfiles.getItemCount(); i++) {
      if (Comparing.strEqual(((InspectionProfileImpl)myProfiles.getItemAt(i)).getName(), name)) {
        myProfiles.setSelectedIndex(i);
        break;
      }
    }
  }

  public void selectInspectionTool(String selectedToolShortName) {
    getSelectedPanel().selectInspectionTool(selectedToolShortName);
  }

  protected SingleInspectionProfilePanel getSelectedPanel() {
    return myPanels.get(((Profile)myProfiles.getSelectedItem()).getName());
  }

  public InspectionProfileImpl getSelectedObject() {
    return (InspectionProfileImpl)myProfiles.getSelectedItem();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    assert inspectionProfile != null;
    return myPanels.get(inspectionProfile.getName()).getTree();
  }
}
