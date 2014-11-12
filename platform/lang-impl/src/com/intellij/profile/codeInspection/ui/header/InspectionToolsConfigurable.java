/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 31-Jul-2006
 * Time: 17:44:39
 */
package com.intellij.profile.codeInspection.ui.header;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.*;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public abstract class InspectionToolsConfigurable extends BaseConfigurable
  implements ErrorsConfigurable, SearchableConfigurable, Configurable.NoScroll {
  public static final String ID = "Errors";
  public static final String DISPLAY_NAME = "Inspections";
  private static final Logger LOG = Logger.getInstance("#" + InspectionToolsConfigurable.class.getName());
  protected final InspectionProfileManager myProfileManager;
  protected final InspectionProjectProfileManager myProjectProfileManager;
  private final CardLayout myLayout = new CardLayout();
  private final AuxiliaryRightPanel myAuxiliaryRightPanel;
  private final JPanel myProfilesHolder;
  private final Map<Profile, SingleInspectionProfilePanel> myPanels =
    new HashMap<Profile, SingleInspectionProfilePanel>();
  private final List<String> myDeletedProfiles = new ArrayList<String>();
  private final String myHeaderTitle = "Profile:";
  protected ProfilesConfigurableComboBox myProfiles;
  private JPanel myPanel;
  private JPanel myWholePanel;
  private JComponent myManageButton;
  private Alarm mySelectionAlarm;

  public InspectionToolsConfigurable(@NotNull final InspectionProjectProfileManager projectProfileManager,
                                     InspectionProfileManager profileManager) {
    myWholePanel = new JPanel();

    myWholePanel.setLayout(new BorderLayout());

    final JPanel toolbar = new JPanel();
    toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));

    myPanel = new JPanel();

    myWholePanel.add(toolbar, BorderLayout.PAGE_START);
    myWholePanel.add(myPanel, BorderLayout.CENTER);

    myProfiles = new ProfilesConfigurableComboBox(new ListCellRendererWrapper<Profile>() {
      @Override
      public void customize(final JList list, final Profile value, final int index, final boolean selected, final boolean hasFocus) {
        final String profileName = value.getName();
        final SingleInspectionProfilePanel singleInspectionProfilePanel = myPanels.get(value);
        final boolean isShared = singleInspectionProfilePanel != null ? singleInspectionProfilePanel.isProfileShared() : false;
        setIcon(isShared ? AllIcons.General.ProjectSettings : AllIcons.General.Settings);
        setText(profileName);
      }
    }) {
      @Override
      public void onProfileChosen(InspectionProfileImpl inspectionProfile) {
        myLayout.show(myPanel, inspectionProfile.getName());
        myAuxiliaryRightPanel.showDescription(inspectionProfile.getDescription());
      }
    };
    myProfilesHolder = new JPanel();
    myProfilesHolder.setLayout(new CardLayout());


    myManageButton = new ManageButton(new ManageButtonBuilder() {
      @Override
      public boolean isSharedToTeamMembers() {
        SingleInspectionProfilePanel panel = getSelectedPanel();
        return panel != null && panel.isProfileShared();
      }

      @Override
      public void setShareToTeamMembers(boolean share) {
        final SingleInspectionProfilePanel panel = getSelectedPanel();
        LOG.assertTrue(panel != null, "No settings panel for: " + getSelectedObject());
        panel.setProfileShared(share);
        myProfiles.repaint();
      }

      @Override
      public void copy() {
        final InspectionProfileImpl newProfile = copyToNewProfile(0, getSelectedObject(), getProject());
        if (newProfile != null) {
          final InspectionProfileImpl modifiableModel = (InspectionProfileImpl)newProfile.getModifiableModel();
          modifiableModel.setModified(true);
          modifiableModel.setLocal(true);
          addProfile(modifiableModel);
          rename(modifiableModel);
        }
      }

      @Override
      public boolean canRename() {
        final InspectionProfileImpl profile = getSelectedObject();
        return !profile.isProfileLocked();
      }

      @Override
      public void rename() {
        rename(getSelectedObject());
      }

      private void rename(@NotNull final InspectionProfileImpl inspectionProfile) {
        final String initialName = inspectionProfile.getName();
        myProfiles.showEditCard(inspectionProfile.getName(), new SaveInputComponentValidator() {
          @Override
          public void doSave(@NotNull String text) {
            if (!text.equals(initialName)) {
              deleteProfile(inspectionProfile);
              myProfiles.getModel().removeElement(inspectionProfile);
              inspectionProfile.setName(text);
              inspectionProfile.setModified(true);
              putProfile(inspectionProfile, myPanels.remove(inspectionProfile));
              addProfile(inspectionProfile);
            }
            myProfiles.showComboBoxCard();
          }

          @Override
          public boolean checkValid(@NotNull String text) {
            final boolean isValid = text.equals(initialName) || !hasName(text);
            if (isValid) {
              myAuxiliaryRightPanel.showDescription(getSelectedObject().getDescription());
            } else {
              myAuxiliaryRightPanel.showError("Name is already in use. Please change name to unique.");
            }
            return isValid;
          }
        });
      }

      @Override
      public boolean canDelete() {
        return isDeleteEnabled(myProfiles.getSelectedProfile());
      }

      @Override
      public void delete() {
        final InspectionProfileImpl selectedProfile = myProfiles.getSelectedProfile();
        myProfiles.getModel().removeElement(selectedProfile);
        myDeletedProfiles.add(selectedProfile.getName());
      }

      @Override
      public boolean canEditDescription() {
        return true;
      }

      @Override
      public void editDescription() {
        myAuxiliaryRightPanel.editDescription(getSelectedObject().getDescription());
      }

      @Override
      public void export() {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setDescription("Choose directory to store profile file");
        FileChooser.chooseFile(descriptor, getProject(), myWholePanel, null, new Consumer<VirtualFile>() {
          @Override
          public void consume(VirtualFile file) {
            final Element element = new Element("inspections");
            try {
              final SingleInspectionProfilePanel panel = getSelectedPanel();
              LOG.assertTrue(panel != null);
              final InspectionProfileImpl profile = getSelectedObject();
              LOG.assertTrue(true);
              profile.writeExternal(element);
              final String filePath =
                FileUtil.toSystemDependentName(file.getPath()) + File.separator + FileUtil.sanitizeFileName(profile.getName()) + ".xml";
              if (new File(filePath).isFile()) {
                if (Messages
                      .showOkCancelDialog(myWholePanel, "File \'" + filePath + "\' already exist. Do you want to overwrite it?", "Warning",
                                          Messages.getQuestionIcon()) != Messages.OK) {
                  return;
                }
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
      }

      @Override
      public void doImport() {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return file.getFileType().equals(StdFileTypes.XML);
          }
        };
        descriptor.setDescription("Choose profile file");
        FileChooser.chooseFile(descriptor, getProject(), myWholePanel, null, new Consumer<VirtualFile>() {
          @Override
          public void consume(VirtualFile file) {
            if (file == null) return;
            InspectionProfileImpl profile =
              new InspectionProfileImpl("TempProfile", InspectionToolRegistrar.getInstance(), myProfileManager);
            try {
              Element rootElement = JDOMUtil.loadDocument(VfsUtilCore.virtualToIoFile(file)).getRootElement();
              if (Comparing.strEqual(rootElement.getName(), "component")) {//import right from .idea/inspectProfiles/xxx.xml
                rootElement = rootElement.getChildren().get(0);
              }
              final Set<String> levels = new HashSet<String>();
              for (Object o : rootElement.getChildren("inspection_tool")) {
                final Element inspectElement = (Element)o;
                levels.add(inspectElement.getAttributeValue("level"));
                for (Object s : inspectElement.getChildren("scope")) {
                  levels.add(((Element)s).getAttributeValue("level"));
                }
              }
              for (Iterator<String> iterator = levels.iterator(); iterator.hasNext(); ) {
                String level = iterator.next();
                if (myProfileManager.getOwnSeverityRegistrar().getSeverity(level) != null) {
                  iterator.remove();
                }
              }
              if (!levels.isEmpty()) {
                if (Messages.showYesNoDialog(myWholePanel, "Undefined severities detected: " +
                                                           StringUtil.join(levels, ", ") +
                                                           ". Do you want to create them?", "Warning", Messages.getWarningIcon()) ==
                    Messages.YES) {
                  for (String level : levels) {
                    final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
                    HighlightInfoType.HighlightInfoTypeImpl info =
                      new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(level, 50),
                                                                  com.intellij.openapi.editor.colors.TextAttributesKey
                                                                    .createTextAttributesKey(level));
                    myProfileManager.getOwnSeverityRegistrar()
                      .registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info),
                                        textAttributes.getErrorStripeColor());
                  }
                }
              }
              profile.readExternal(rootElement);
              profile.setLocal(true);
              profile.initInspectionTools(getProject());
              if (getProfilePanel(profile) != null) {
                if (Messages.showOkCancelDialog(myWholePanel, "Profile with name \'" +
                                                              profile.getName() +
                                                              "\' already exists. Do you want to overwrite it?", "Warning",
                                                Messages.getInformationIcon()) != Messages.OK) {
                  return;
                }
              }
              final ModifiableModel model = profile.getModifiableModel();
              model.setModified(true);
              addProfile((InspectionProfileImpl)model);

              //TODO myDeletedProfiles ? really need this
              myDeletedProfiles.remove(profile.getName());
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
      }
    }).build();

    myAuxiliaryRightPanel = new AuxiliaryRightPanel(new AuxiliaryRightPanel.DescriptionSaveListener() {
      @Override
      public void saveDescription(@NotNull String description) {
        getSelectedObject().setDescription(description);
        myAuxiliaryRightPanel.showDescription(description);
      }
    });

    toolbar.setLayout(new GridBagLayout());
    toolbar.add(new JLabel(myHeaderTitle),
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(10, 5, 0, 0), 0,
                                       0));
    toolbar.add(myProfiles,
                new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(10, 5, 0, 0), 0,
                                       0));
    toolbar.add(myManageButton,
                new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(10, 5, 0, 0), 0,
                                       0));
    toolbar.add(myAuxiliaryRightPanel,
                new GridBagConstraints(3, 0, 1, 1, 1.0, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(10, 5, 0, 0), 0,
                                       0));

    ((InspectionManagerEx)InspectionManager.getInstance(projectProfileManager.getProject())).buildInspectionSearchIndexIfNecessary();
    myProjectProfileManager = projectProfileManager;
    myProfileManager = profileManager;
  }

  private Project getProject() {
    return myProjectProfileManager.getProject();
  }

  @Nullable
  private InspectionProfileImpl copyToNewProfile(final int initValue, ModifiableModel selectedProfile, @NotNull Project project) {
    String profileDefaultName = selectedProfile.getName();
    do {
      profileDefaultName += " (copy)";
    }
    while (hasName(profileDefaultName));

    final ProfileManager profileManager = selectedProfile.getProfileManager();
    InspectionProfileImpl inspectionProfile =
      new InspectionProfileImpl(profileDefaultName, InspectionToolRegistrar.getInstance(), profileManager);

    inspectionProfile.copyFrom(selectedProfile);
    inspectionProfile.setName(profileDefaultName);
    inspectionProfile.initInspectionTools(project);
    inspectionProfile.setModified(true);
    return inspectionProfile;
  }

  private void addProfile(InspectionProfileImpl model) {
    final String modelName = model.getName();
    final SingleInspectionProfilePanel panel = createPanel(model, modelName);
    myPanel.add(modelName, panel);
    myPanel.add(modelName, panel);
    if (!myPanels.containsKey(modelName)) {
      //noinspection unchecked
      myProfiles.getModel().addElement(model);
    }
    putProfile(model, panel);
    myProfiles.selectProfile(model);
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getHelpTopic() {
    return "preferences.inspections";
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return new Runnable() {
      @Override
      public void run() {
        SingleInspectionProfilePanel panel = getSelectedPanel();
        if (panel != null) {
          panel.setFilter(option);
        }
      }
    };
  }

  @Override
  public JComponent createComponent() {
    myPanel.setLayout(myLayout);
    return myWholePanel;
  }

  protected abstract InspectionProfileImpl getCurrentProfile();

  @Override
  public boolean isModified() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      if (panel.isModified()) return true;
    }
    if (getProfiles().size() != myPanels.size()) return true;
    return !myDeletedProfiles.isEmpty();
  }

  @Override
  public void apply() throws ConfigurationException {
    final Map<Profile, SingleInspectionProfilePanel> panels = new LinkedHashMap<Profile, SingleInspectionProfilePanel>();
    for (final Profile inspectionProfile : myPanels.keySet()) {
      if (myDeletedProfiles.remove(inspectionProfile.getName())) {
        deleteProfile(getProfilePanel(inspectionProfile).getSelectedProfile());
      }
      else {
        final SingleInspectionProfilePanel panel = getProfilePanel(inspectionProfile);
        panel.apply();
        final ModifiableModel profile = panel.getSelectedProfile();
        panels.put(profile, panel);
      }
    }
    myPanels.clear();
    myPanels.putAll(panels);
  }

  private SingleInspectionProfilePanel getProfilePanel(Profile inspectionProfile) {
    return myPanels.get(inspectionProfile);
  }

  private void putProfile(Profile profile, SingleInspectionProfilePanel panel) {
    myPanels.put(profile, panel);
  }

  protected void deleteProfile(Profile profile) {
    final String name = profile.getName();
    if (myProfileManager.getProfile(name, false) != null) {
      myProfileManager.deleteProfile(name);
    }
    if (myProjectProfileManager.getProfile(name, false) != null) {
      myProjectProfileManager.deleteProfile(name);
    }
  }

  protected boolean acceptTool(InspectionToolWrapper entry) {
    return true;
  }

  @Override
  public void reset() {
    myDeletedProfiles.clear();
    myPanels.clear();
    final Collection<Profile> profiles = getProfiles();
    final List<Profile> modifiableProfiles = new ArrayList<Profile>(profiles.size());
    for (Profile profile : profiles) {
      final String profileName = profile.getName();
      final ModifiableModel modifiableProfile = ((InspectionProfileImpl)profile).getModifiableModel();
      modifiableProfiles.add(modifiableProfile);
      final SingleInspectionProfilePanel panel = createPanel((InspectionProfileImpl)modifiableProfile, profileName);
      putProfile(modifiableProfile, panel);
      myPanel.add(profileName, panel);
    }
    myProfiles.reset(modifiableProfiles);
    myAuxiliaryRightPanel.showDescription(getSelectedObject().getDescription());

    final InspectionProfileImpl inspectionProfile = getCurrentProfile();
    myProfiles.selectProfile(inspectionProfile);
    myLayout.show(myPanel, inspectionProfile.getName());
    //myDeleteButton.setEnabled(isDeleteEnabled(inspectionProfile));
    final SingleInspectionProfilePanel panel = getSelectedPanel();
    if (panel != null) {
      panel.setVisible(true);//make sure that UI was initialized
      mySelectionAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (mySelectionAlarm != null) {
            mySelectionAlarm.addRequest(new Runnable() {
              @Override
              public void run() {
                panel.updateSelection();
              }
            }, 200);
          }
        }
      });
    }
  }

  private SingleInspectionProfilePanel createPanel(InspectionProfileImpl profile, String profileName) {
    return new SingleInspectionProfilePanel(myProjectProfileManager, profileName, profile) {
      @Override
      protected boolean accept(InspectionToolWrapper entry) {
        return super.accept(entry) && InspectionToolsConfigurable.this.acceptTool(entry);
      }
    };
  }

  private boolean isDeleteEnabled(@NotNull InspectionProfileImpl inspectionProfile) {
    final ProfileManager profileManager = inspectionProfile.getProfileManager();

    boolean projectProfileFound = false;
    boolean ideProfileFound = false;

    final ComboBoxModel model = myProfiles.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      Profile profile = (Profile)model.getElementAt(i);
      if (inspectionProfile == profile) continue;
      final boolean isProjectProfile = profile.getProfileManager() == myProjectProfileManager;
      projectProfileFound |= isProjectProfile;
      ideProfileFound |= !isProjectProfile;

      if (ideProfileFound && projectProfileFound) break;
    }

    return profileManager == myProjectProfileManager ? projectProfileFound : ideProfileFound;
  }

  protected Collection<Profile> getProfiles() {
    final Collection<Profile> result = new ArrayList<Profile>();
    result.addAll(new TreeSet<Profile>(myProfileManager.getProfiles()));
    result.addAll(myProjectProfileManager.getProfiles());
    return result;
  }

  @Override
  public void disposeUIResources() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      panel.disposeUI();
    }
    myPanels.clear();
    if (mySelectionAlarm != null) {
      Disposer.dispose(mySelectionAlarm);
      mySelectionAlarm = null;
    }
  }

  @Override
  public void selectProfile(String name) {
    myProfiles.selectProfile(name);
  }

  @Override
  public void selectInspectionTool(String selectedToolShortName) {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    final SingleInspectionProfilePanel panel = getProfilePanel(inspectionProfile);
    LOG.assertTrue(panel != null, "No settings panel for: " + inspectionProfile + "; " + configuredProfiles());
    panel.selectInspectionTool(selectedToolShortName);
  }

  protected SingleInspectionProfilePanel getSelectedPanel() {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    return getProfilePanel(inspectionProfile);
  }

  private String configuredProfiles() {
    return "configured profiles: " + StringUtil.join(myPanels.keySet(), ", ");
  }

  protected Collection<String> getKnownNames() {
    return ContainerUtil.map(myPanels.keySet(), new Function<Profile, String>() {
      @Override
      public String fun(Profile profile) {
        return profile.getName();
      }
    });
  }

  private boolean hasName(final @NotNull String name) {
    for (Profile profile : myPanels.keySet()) {
      if (name.equals(profile.getName())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public InspectionProfileImpl getSelectedObject() {
    return myProfiles.getSelectedProfile();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    return getProfilePanel(inspectionProfile).getPreferredFocusedComponent();
  }
}
