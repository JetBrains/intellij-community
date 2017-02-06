/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.profile.codeInspection.ui.header;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;

public abstract class InspectionToolsConfigurable extends BaseConfigurable
  implements ErrorsConfigurable, SearchableConfigurable, Configurable.NoScroll {
  public static final String ID = "Errors";
  public static final String DISPLAY_NAME = "Inspections";
  private static final String HEADER_TITLE = "Profile:";

  private static final Logger LOG = Logger.getInstance(InspectionToolsConfigurable.class);
  private static final Pattern COPIED_PROFILE_SUFFIX_PATTERN = Pattern.compile("(.*\\s*copy)\\s*(\\d*)");

  protected final BaseInspectionProfileManager myApplicationProfileManager;
  protected final ProjectInspectionProfileManager myProjectProfileManager;
  private final List<SingleInspectionProfilePanel> myPanels = new ArrayList<>();
  private final List<InspectionProfileModifiableModel> myDeletedProfiles = new SmartList<>();
  protected ProfilesChooser myProfiles;
  private JPanel myProfilePanelHolder;
  private AuxiliaryRightPanel myAuxiliaryRightPanel;
  private Alarm mySelectionAlarm;

  public InspectionToolsConfigurable(@NotNull ProjectInspectionProfileManager projectProfileManager) {
    myProjectProfileManager = projectProfileManager;
    myApplicationProfileManager = (BaseInspectionProfileManager)InspectionProfileManager.getInstance();
  }

  private static JComponent withBorderOnTop(final JComponent component) {
    final JPanel panel = new JPanel();
    panel.add(component);
    panel.setBorder(IdeBorderFactory.createEmptyBorder(UIUtil.isUnderDarcula() ? 10 : 13, 0, 0, 0));
    return panel;
  }

  private Project getProject() {
    return myProjectProfileManager.getProject();
  }

  @NotNull
  private InspectionProfileImpl copyToNewProfile(@NotNull InspectionProfileImpl selectedProfile,
                                                 @NotNull Project project,
                                                 boolean modifyName,
                                                 boolean modifyLevel) {
    LOG.assertTrue(modifyLevel || modifyName);
    String profileDefaultName = selectedProfile.getName();

    final boolean isProjectLevel = selectedProfile.isProjectLevel() ^ modifyLevel;
    if (modifyName) {
      final Matcher matcher = COPIED_PROFILE_SUFFIX_PATTERN.matcher(profileDefaultName);
      int nextIdx;
      if (matcher.matches()) {
        profileDefaultName = matcher.group(1);
        nextIdx = matcher.group(2).isEmpty() ? 1 : Integer.valueOf(matcher.group(2));
      }
      else {
        profileDefaultName += " copy";
        nextIdx = 1;
      }
      if (hasName(profileDefaultName, isProjectLevel)) {
        String currentProfileDefaultName;
        do {
          currentProfileDefaultName = profileDefaultName + " " + String.valueOf(nextIdx);
          nextIdx++;
        }
        while (hasName(currentProfileDefaultName, isProjectLevel));
        profileDefaultName = currentProfileDefaultName;
      }
    }

    BaseInspectionProfileManager profileManager = isProjectLevel ? myProjectProfileManager : myApplicationProfileManager;
    InspectionProfileImpl inspectionProfile =
      new InspectionProfileImpl(profileDefaultName, InspectionToolRegistrar.getInstance(), profileManager);

    inspectionProfile.copyFrom(selectedProfile);
    inspectionProfile.setName(profileDefaultName);
    inspectionProfile.initInspectionTools(project);
    inspectionProfile.setProjectLevel(isProjectLevel);

    InspectionProfileModifiableModel modifiableModel = new InspectionProfileModifiableModel(inspectionProfile);
    modifiableModel.setModified(true);
    addProfile(modifiableModel);
    return modifiableModel;
  }

  protected void addProfile(InspectionProfileModifiableModel model) {
    final SingleInspectionProfilePanel panel = createPanel(model);
    myPanels.add(panel);
    myProfilePanelHolder.add(panel);
    myProfiles.getProfilesComboBox().addProfile(model);
    myProfiles.getProfilesComboBox().selectProfile(model);
  }

  protected boolean setActiveProfileAsDefaultOnApply() {
    return true;
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
    return () -> {
      SingleInspectionProfilePanel panel = getSelectedPanel();
      if (panel != null) {
        panel.setFilter(option);
      }
    };
  }

  @Override
  public JComponent createComponent() {
    final JPanel wholePanel = new JPanel();
    wholePanel.setLayout(new BorderLayout());

    final JPanel toolbar = new JPanel();
    toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));

    myProfilePanelHolder = new JPanel() {
      @Override
      public void doLayout() {
        Rectangle bounds = new Rectangle(getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());
        for (Component component : getComponents()) {
          component.setBounds(bounds);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        for (Component component : getComponents()) {
          if (component.isVisible()) {
            return component.getPreferredSize();
          }
        }
        return super.getPreferredSize();
      }

      @Override
      public Dimension getMinimumSize() {
        for (Component component : getComponents()) {
          if (component.isVisible()) {
            return component.getMinimumSize();
          }
        }
        return super.getMinimumSize();
      }
    };

    wholePanel.add(toolbar, BorderLayout.PAGE_START);
    wholePanel.add(myProfilePanelHolder, BorderLayout.CENTER);

    myAuxiliaryRightPanel = new AuxiliaryRightPanel(new AuxiliaryRightPanel.DescriptionSaveListener() {
      @Override
      public void saveDescription(@NotNull String description) {
        InspectionProfileModifiableModel inspectionProfile = getSelectedObject();
        if (!Comparing.strEqual(description, inspectionProfile.getDescription())) {
          inspectionProfile.setDescription(description);
          inspectionProfile.setModified(true);
        }
        myAuxiliaryRightPanel.showDescription(description);
      }

      @Override
      public void cancel() {
        myAuxiliaryRightPanel.showDescription(getSelectedObject().getDescription());
      }
    });

    myProfiles = new ProfilesChooser(myProjectProfileManager.getProject()) {
      @Override
      public void onProfileChosen(InspectionProfileImpl inspectionProfile) {
        showProfile(inspectionProfile);
        myAuxiliaryRightPanel.showDescription(inspectionProfile.getDescription());
      }
    };
    JPanel profilesHolder = new JPanel();
    profilesHolder.setLayout(new CardLayout());


    JComponent manageButton = new ManageButton(new ManageButtonBuilder() {
      @Override
      public boolean isProjectLevel() {
        SingleInspectionProfilePanel panel = getSelectedPanel();
        return panel != null && panel.getProfile().isProjectLevel();
      }

      @Override
      public boolean canChangeProfileLevel() {
        return !hasName(getSelectedPanel().getProfile().getName(), !isProjectLevel());
      }

      @Override
      public void copyToAnotherLevel() {
        final SingleInspectionProfilePanel selectedPanel = getSelectedPanel();
        LOG.assertTrue(selectedPanel != null, "No settings selectedPanel for: " + getSelectedObject());
        copyToNewProfile(getSelectedObject(), getProject(), false, true);
      }

      @Override
      public void copy() {
        rename(copyToNewProfile(getSelectedObject(), getProject(), true, false));
      }

      @Override
      public void rename() {
        rename(getSelectedObject());
      }

      private void rename(@NotNull final InspectionProfileImpl inspectionProfile) {
        final String initialName = getSelectedPanel().getProfile().getName();
        myProfiles.showEditCard(initialName, new SaveInputComponentValidator() {
          @Override
          public void doSave(@NotNull String text) {
            if (!text.equals(initialName)) {
              inspectionProfile.setName(text);
              myProfiles.getProfilesComboBox().resort();
            }
            myProfiles.showComboBoxCard();
          }

          @Override
          public boolean checkValid(@NotNull String text) {
            final boolean isValid = text.equals(initialName) || !hasName(text, inspectionProfile.isProjectLevel());
            if (isValid) {
              myAuxiliaryRightPanel.showDescription(getSelectedObject().getDescription());
            }
            else {
              myAuxiliaryRightPanel.showError("Name is already in use. Please change name to unique.");
            }
            return isValid;
          }

          @Override
          public void cancel() {
            myProfiles.showComboBoxCard();
            myAuxiliaryRightPanel.showDescription(getSelectedObject().getDescription());
          }
        });
      }

      @Override
      public boolean canDelete() {
        return isDeleteEnabled(myProfiles.getProfilesComboBox().getSelectedProfile());
      }

      @Override
      public void delete() {
        InspectionProfileModifiableModel selectedProfile = myProfiles.getProfilesComboBox().getSelectedProfile();
        myProfiles.getProfilesComboBox().removeProfile(selectedProfile);
        myPanels.remove(getProfilePanel(selectedProfile));
        myDeletedProfiles.add(selectedProfile);
        myProfiles.getProfilesComboBox().setSelectedIndex(0);
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
      public boolean hasDescription() {
        return !StringUtil.isEmpty(getSelectedObject().getDescription());
      }

      @Override
      public void export() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setDescription("Choose directory to store profile file");
        FileChooser.chooseFile(descriptor, getProject(), wholePanel, null, dir -> {
          try {
            SingleInspectionProfilePanel panel = getSelectedPanel();
            LOG.assertTrue(panel != null);
            InspectionProfileImpl profile = getSelectedObject();
            LOG.assertTrue(true);
            Element element = profile.writeScheme(false);
            File file = new File(FileUtil.toSystemDependentName(dir.getPath()), sanitizeFileName(profile.getName()) + ".xml");
            if (file.isFile() &&
                Messages.showOkCancelDialog(wholePanel, "File \'" + file + "\' already exist. Do you want to overwrite it?", "Warning",
                                            Messages.getQuestionIcon()) != Messages.OK) {
              return;
            }

            JDOMUtil.writeParent(element, file, "\n");
          }
          catch (IOException e1) {
            LOG.error(e1);
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
        FileChooser.chooseFile(descriptor, getProject(), wholePanel, null, file -> {
          if (file != null) {
            final InspectionProfileImpl profile;
            try {
              profile = importInspectionProfile(JDOMUtil.load(file.getInputStream()), myApplicationProfileManager, getProject(), wholePanel);
              final SingleInspectionProfilePanel existed = getProfilePanel(profile);
              if (existed != null) {
                if (Messages.showOkCancelDialog(wholePanel, "Profile with name \'" +
                                                            profile.getName() +
                                                            "\' already exists. Do you want to overwrite it?", "Warning",
                                                Messages.getInformationIcon()) != Messages.OK) {
                  return;
                }
                myProfiles.getProfilesComboBox().removeProfile(existed.getProfile());
                myPanels.remove(existed);
              }
              InspectionProfileModifiableModel model = new InspectionProfileModifiableModel(profile);
              model.setModified(true);
              addProfile(model);
              selectProfile(model);
            }
            catch (JDOMException | InvalidDataException | IOException e) {
              LOG.error(e);
            }
          }
        });
      }
    }).build();


    toolbar.setLayout(new GridBagLayout());
    final JLabel headerTitleLabel = new JLabel(HEADER_TITLE);
    headerTitleLabel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    toolbar.add(headerTitleLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                         JBUI.emptyInsets(), 0, 0));

    toolbar.add(myProfiles, new GridBagConstraints(1, 0, 1, 1, 0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
                                                   JBUI.insetsLeft(6), 0, 0));

    toolbar.add(withBorderOnTop(manageButton), new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
                                                                      JBUI.insetsLeft(10), 0, 0));

    toolbar.add(myAuxiliaryRightPanel, new GridBagConstraints(3, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                                              JBUI.insetsLeft(15), 0, 0));

    return wholePanel;
  }

  public static InspectionProfileImpl importInspectionProfile(@NotNull Element rootElement,
                                                              @NotNull BaseInspectionProfileManager profileManager,
                                                              @NotNull Project project,
                                                              @Nullable JPanel anchorPanel) {
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode) {
      LOG.assertTrue(anchorPanel != null);
    }
    InspectionProfileImpl profile =
      new InspectionProfileImpl("TempProfile", InspectionToolRegistrar.getInstance(), profileManager);
    if (Comparing.strEqual(rootElement.getName(), "component")) {
      //import right from .idea/inspectProfiles/xxx.xml
      rootElement = rootElement.getChildren().get(0);
    }
    final Set<String> levels = new HashSet<>();
    for (Element inspectElement : rootElement.getChildren("inspection_tool")) {
      addLevelIfNotNull(levels, inspectElement);
      for (Element s : inspectElement.getChildren("scope")) {
        addLevelIfNotNull(levels, s);
      }
    }
    for (Iterator<String> iterator = levels.iterator(); iterator.hasNext(); ) {
      String level = iterator.next();
      if (profileManager.getOwnSeverityRegistrar().getSeverity(level) != null) {
        iterator.remove();
      }
    }
    if (!levels.isEmpty()) {
      if (!unitTestMode) {
        if (Messages.showYesNoDialog(anchorPanel, "Undefined severities detected: " +
                                                  StringUtil.join(levels, ", ") +
                                                  ". Do you want to create them?", "Warning", Messages.getWarningIcon()) ==
            Messages.YES) {
          for (String level : levels) {
            final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
            HighlightInfoType.HighlightInfoTypeImpl info =
              new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(level, 50),
                                                          TextAttributesKey
                                                            .createTextAttributesKey(level));
            profileManager.getOwnSeverityRegistrar()
              .registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info),
                                textAttributes.getErrorStripeColor());
          }
        }
      } else {
        throw new AssertionError("All of levels must exist in unit-test mode, but actual not exist levels = " + levels);
      }
    }
    profile.readExternal(rootElement);
    profile.setProjectLevel(false);
    profile.initInspectionTools(project);
    return profile;
  }

  private static void addLevelIfNotNull(Set<String> levels, Element inspectElement) {
    final String level = inspectElement.getAttributeValue("level");
    if (level != null) {
      levels.add(level);
    }
  }

  protected abstract InspectionProfileImpl getCurrentProfile();

  @Override
  public boolean isModified() {
    final InspectionProfileImpl selectedProfile = getSelectedObject();
    final InspectionProfileImpl currentProfile = getCurrentProfile();
    if (!Comparing.equal(selectedProfile, currentProfile)) {
      return true;
    }
    for (SingleInspectionProfilePanel panel : myPanels) {
      if (panel.isModified()) return true;
    }
    return getProfiles().size() != myPanels.size() || !myDeletedProfiles.isEmpty();
  }

  @Override
  public void apply() {
    for (InspectionProfileModifiableModel profile : myDeletedProfiles) {
      profile.getProfileManager().deleteProfile(profile.getSource());
    }
    myDeletedProfiles.clear();

    SingleInspectionProfilePanel selectedPanel = getSelectedPanel();
    for (SingleInspectionProfilePanel panel : myPanels) {
      panel.apply();
      if (setActiveProfileAsDefaultOnApply() && panel == selectedPanel) {
        applyRootProfile(panel.getProfile().getName(), panel.getProfile().isProjectLevel());
      }
    }
  }

  protected abstract void applyRootProfile(@NotNull String name, boolean isProjectLevel);

  protected boolean acceptTool(InspectionToolWrapper entry) {
    return true;
  }

  @Override
  public void reset() {
    doReset();
  }

  private void doReset() {
    myDeletedProfiles.clear();
    disposeUIResources();
    final Collection<InspectionProfileImpl> profiles = getProfiles();
    final List<InspectionProfileModifiableModel> modifiableProfiles = new ArrayList<>(profiles.size());
    for (InspectionProfileImpl profile : profiles) {
      InspectionProfileModifiableModel inspectionProfile = new InspectionProfileModifiableModel(profile);
      modifiableProfiles.add(inspectionProfile);
      final SingleInspectionProfilePanel panel = createPanel(inspectionProfile);
      myPanels.add(panel);
      myProfilePanelHolder.add(panel);
    }
    myProfiles.getProfilesComboBox().reset(modifiableProfiles);
    myAuxiliaryRightPanel.showDescription(getSelectedObject().getDescription());
    final InspectionProfileImpl inspectionProfile = getCurrentProfile();
    myProfiles.getProfilesComboBox().selectProfile(inspectionProfile);
    showProfile(inspectionProfile);
    final SingleInspectionProfilePanel panel = getSelectedPanel();
    if (panel != null) {
      panel.setVisible(true);//make sure that UI was initialized
      mySelectionAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
      mySelectionAlarm.cancelAllRequests();
      mySelectionAlarm.addRequest(panel::updateSelection, 200);
    }
  }

  private SingleInspectionProfilePanel createPanel(InspectionProfileModifiableModel profile) {
    return new SingleInspectionProfilePanel(myProjectProfileManager, profile) {
      @Override
      protected boolean accept(InspectionToolWrapper entry) {
        return super.accept(entry) && acceptTool(entry);
      }
    };
  }

  private boolean isDeleteEnabled(@NotNull InspectionProfileImpl inspectionProfile) {
    boolean projectProfileFound = false;
    boolean ideProfileFound = false;

    for (InspectionProfileImpl profile : myProfiles.getProfilesComboBox().getProfiles()) {
      if (inspectionProfile == profile) continue;
      final boolean isProjectProfile = profile.getProfileManager() == myProjectProfileManager;
      projectProfileFound |= isProjectProfile;
      ideProfileFound |= !isProjectProfile;

      if (ideProfileFound && projectProfileFound) break;
    }

    return inspectionProfile.getProfileManager() == myProjectProfileManager ? projectProfileFound : ideProfileFound;
  }

  protected Collection<InspectionProfileImpl> getProfiles() {
    final Collection<InspectionProfileImpl> result = new ArrayList<>();
    result.addAll(new TreeSet<>(myApplicationProfileManager.getProfiles()));
    result.addAll(myProjectProfileManager.getProfiles());
    return result;
  }

  @Override
  public void disposeUIResources() {
    for (SingleInspectionProfilePanel panel : myPanels) {
      panel.disposeUI();
    }
    myPanels.clear();
    if (mySelectionAlarm != null) {
      Disposer.dispose(mySelectionAlarm);
      mySelectionAlarm = null;
    }
  }

  @Override
  public void selectProfile(InspectionProfileImpl profile) {
    myProfiles.getProfilesComboBox().selectProfile(profile);
  }

  private SingleInspectionProfilePanel getProfilePanel(InspectionProfileImpl profile) {
    for (SingleInspectionProfilePanel panel : myPanels) {
      if (panel.getProfile().equals(profile)) {
        return panel;
      }
    }
    return null;
  }

  @Override
  public void selectInspectionTool(String selectedToolShortName) {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    final SingleInspectionProfilePanel panel = getProfilePanel(inspectionProfile);
    LOG.assertTrue(panel != null, "No settings panel for: " + inspectionProfile + "; " + configuredProfiles());
    panel.selectInspectionTool(selectedToolShortName);
  }

  @Override
  public void selectInspectionGroup(String[] groupPath) {
    getProfilePanel(getSelectedObject()).selectInspectionGroup(groupPath);
  }

  protected SingleInspectionProfilePanel getSelectedPanel() {
    final InspectionProfileImpl inspectionProfile = getSelectedObject();
    return getProfilePanel(inspectionProfile);
  }

  private String configuredProfiles() {
    return "configured profiles: " + StringUtil.join(myPanels.stream().map(p -> p.getProfile().getName()).collect(Collectors.toList()), ", ");
  }

  private boolean hasName(@NotNull final String name, boolean shared) {
    for (SingleInspectionProfilePanel p : myPanels) {
      if (name.equals(p.getProfile().getName()) && shared == p.getProfile().isProjectLevel()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public InspectionProfileModifiableModel getSelectedObject() {
    return myProfiles.getProfilesComboBox().getSelectedProfile();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final SingleInspectionProfilePanel panel = getSelectedPanel();
    return panel == null ? null : panel.getPreferredFocusedComponent();
  }

  private void showProfile(InspectionProfileImpl profile) {
    final SingleInspectionProfilePanel panel = getProfilePanel(profile);
    for (Component component : myProfilePanelHolder.getComponents()) {
      component.setVisible(component == panel);
    }
  }
}
