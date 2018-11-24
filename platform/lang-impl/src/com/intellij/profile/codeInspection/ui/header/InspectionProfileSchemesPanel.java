/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.application.options.schemes.AbstractDescriptionAwareSchemesPanel;
import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.DescriptionAwareSchemeActions;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.util.JdomKt.loadElement;

public class InspectionProfileSchemesPanel extends AbstractDescriptionAwareSchemesPanel<InspectionProfileModifiableModel> {
  private final static Logger LOG = Logger.getInstance(InspectionProfileSchemesPanel.class);

  private final Project myProject;
  private final BaseInspectionProfileManager myAppProfileManager;
  private final BaseInspectionProfileManager myProjectProfileManager;
  private final InspectionToolsConfigurable myConfigurable;
  private final InspectionProfileSchemesModel myModel;

  InspectionProfileSchemesPanel(@NotNull Project project,
                                @NotNull BaseInspectionProfileManager appProfileManager,
                                @NotNull BaseInspectionProfileManager projectProfileManager,
                                @NotNull InspectionToolsConfigurable configurable) {
    myProject = project;
    myAppProfileManager = appProfileManager;
    myProjectProfileManager = projectProfileManager;
    myConfigurable = configurable;
    myModel = new InspectionProfileSchemesModel(appProfileManager, projectProfileManager) {
      @Override
      protected void onProfileRemoved(@NotNull SingleInspectionProfilePanel profilePanel) {
        myConfigurable.removeProfilePanel(profilePanel);
        final List<InspectionProfileModifiableModel> currentProfiles = getModel()
          .getProfilePanels()
          .stream()
          .map(SingleInspectionProfilePanel::getProfile)
          .collect(Collectors.toList());
        resetSchemes(currentProfiles);
        selectScheme(ContainerUtil.getFirstItem(currentProfiles));
      }

      @Override
      protected SingleInspectionProfilePanel createPanel(InspectionProfileModifiableModel model) {
        return myConfigurable.createPanel(model);
      }
    };
  }

  @NotNull
  @Override
  public InspectionProfileSchemesModel getModel() {
    return myModel;
  }

  @Override
  protected boolean supportsProjectSchemes() {
    return true;
  }

  @Override
  protected boolean highlightNonDefaultSchemes() {
    return false;
  }

  @Override
  public boolean useBoldForNonRemovableSchemes() {
    return false;
  }

  @Override
  protected boolean hideDeleteActionIfUnavailable() {
    return false;
  }

  @Override
  protected AbstractSchemeActions<InspectionProfileModifiableModel> createSchemeActions() {
    return new DescriptionAwareSchemeActions<InspectionProfileModifiableModel>(this) {
      @Nullable
      @Override
      public String getDescription(@NotNull InspectionProfileModifiableModel scheme) {
        SingleInspectionProfilePanel inspectionProfile = ((InspectionProfileSchemesModel) getModel()).getProfilePanel(scheme);
        return inspectionProfile.getProfile().getDescription();
      }

      @Override
      protected void setDescription(@NotNull InspectionProfileModifiableModel scheme, @NotNull String newDescription) {
        InspectionProfileModifiableModel inspectionProfile = InspectionProfileSchemesPanel.this.getModel().getProfilePanel(scheme).getProfile();
        if (!Comparing.strEqual(newDescription, inspectionProfile.getDescription())) {
          inspectionProfile.setDescription(newDescription);
          inspectionProfile.setModified(true);
        }
      }

      @Override
      protected void importScheme(@NotNull String importerName) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return file.getFileType().equals(StdFileTypes.XML);
          }
        };
        descriptor.setDescription("Choose profile file");
        FileChooser.chooseFile(descriptor, myProject, null, file -> {
          if (file != null) {
            try {
              InspectionProfileImpl profile = importInspectionProfile(loadElement(file.getInputStream()), myAppProfileManager, myProject);
              if (profile == null) {
                Messages.showErrorDialog(myProject, "File '" + file.getName() + "' has invalid format.", "Inspection Settings");
                return;
              }
              final SingleInspectionProfilePanel existed = InspectionProfileSchemesPanel.this.getModel().getProfilePanel(profile);
              if (existed != null) {
                if (Messages.showOkCancelDialog(myProject, "Profile with name \'" + profile.getName() +
                                                           "\' already exists. Do you want to overwrite it?",
                                                "Overwrite Warning",
                                                "Overwrite", "Cancel",
                                                Messages.getInformationIcon()) != Messages.OK) {
                  return;
                }
                getModel().removeScheme(existed.getProfile());
              }
              InspectionProfileModifiableModel model = new InspectionProfileModifiableModel(profile);
              model.setModified(true);
              addProfile(model);
              selectScheme(model);
            }
            catch (JDOMException | InvalidDataException | IOException e) {
              LOG.error(e);
            }
          }
        });
      }

      @Override
      protected void resetScheme(@NotNull InspectionProfileModifiableModel scheme) {
        final SingleInspectionProfilePanel panel = InspectionProfileSchemesPanel.this.getModel().getProfilePanel(scheme);
        panel.performProfileReset();
      }

      @Override
      protected void duplicateScheme(@NotNull InspectionProfileModifiableModel scheme, @NotNull String newName) {
        final InspectionProfileModifiableModel newProfile = copyToNewProfile(scheme, myProject, newName, false);
        addProfile(newProfile);
        myConfigurable.selectProfile(newProfile);
        selectScheme(newProfile);
      }

      @Override
      protected void onSchemeChanged(@Nullable InspectionProfileModifiableModel scheme) {
        super.onSchemeChanged(scheme);
        if (scheme != null) {
          myConfigurable.selectProfile(scheme);
        }
      }

      @Override
      protected void renameScheme(@NotNull InspectionProfileModifiableModel scheme, @NotNull String newName) {
        scheme.setName(newName);
      }

      @Override
      protected void copyToProject(@NotNull InspectionProfileModifiableModel scheme) {
        copyToAnotherLevel(scheme, true);
      }

      @Override
      protected void copyToIDE(@NotNull InspectionProfileModifiableModel scheme) {
        copyToAnotherLevel(scheme, false);
      }

      @Override
      protected Class<InspectionProfileModifiableModel> getSchemeType() {
        return InspectionProfileModifiableModel.class;
      }

      private void copyToAnotherLevel(InspectionProfileModifiableModel profile, boolean copyToProject) {
        getSchemesPanel().editNewSchemeName(
          profile.getName(),
          copyToProject,
          newName -> {
            final InspectionProfileModifiableModel newProfile = copyToNewProfile(profile, myProject, newName, true);
            addProfile(newProfile);
            selectScheme(newProfile);
          });
      }
    };
  }

  @Override
  protected String getSchemeTypeName() {
    return "Profile";
  }

  void apply() {
    getModel().apply(getSelectedScheme(), (p) -> {
      if (myConfigurable.setActiveProfileAsDefaultOnApply()) {
        myConfigurable.applyRootProfile(p.getName(), p.isProjectLevel());
      }
    });
  }

  void reset() {
    getModel().reset();
    getModel().updatePanel(this);
  }

  @NotNull
  private InspectionProfileModifiableModel copyToNewProfile(@NotNull InspectionProfileImpl selectedProfile,
                                                 @NotNull Project project,
                                                 @NotNull String newName,
                                                 boolean modifyLevel) {
    final boolean isProjectLevel = selectedProfile.isProjectLevel() ^ modifyLevel;

    BaseInspectionProfileManager profileManager = isProjectLevel ? myProjectProfileManager : myAppProfileManager;
    InspectionProfileImpl inspectionProfile =
      new InspectionProfileImpl(newName, InspectionToolRegistrar.getInstance(), profileManager);

    inspectionProfile.copyFrom(selectedProfile);
    inspectionProfile.setName(newName);
    inspectionProfile.initInspectionTools(project);
    inspectionProfile.setProjectLevel(isProjectLevel);

    InspectionProfileModifiableModel modifiableModel = new InspectionProfileModifiableModel(inspectionProfile);
    modifiableModel.setModified(true);
    return modifiableModel;
  }

  private void addProfile(InspectionProfileModifiableModel profile) {
    final InspectionProfileModifiableModel selected = getSelectedScheme();
    getModel().addProfile(profile);
    getModel().updatePanel(this);
    selectScheme(selected);
  }

  @NotNull
  @Override
  protected JComponent getConfigurableFocusComponent() {
    return myConfigurable.getPreferredFocusedComponent();
  }

  @Nullable("returns null if xml has invalid format")
  public static InspectionProfileImpl importInspectionProfile(@NotNull Element rootElement,
                                                              @NotNull BaseInspectionProfileManager profileManager,
                                                              @NotNull Project project) {
    if (Comparing.strEqual(rootElement.getName(), "component")) {
      //import right from .idea/inspectProfiles/xxx.xml
      rootElement = rootElement.getChildren().get(0);
    }

    String profileName = getProfileName(rootElement);
    if (profileName == null) return null;

    InspectionProfileImpl profile = new InspectionProfileImpl(profileName, InspectionToolRegistrar.getInstance(), profileManager);
    final Set<String> levels = new HashSet<>();
    for (Element inspectElement : rootElement.getChildren("inspection_tool")) {
      ContainerUtil.addAllNotNull(levels, inspectElement.getAttributeValue("level"));
      for (Element s : inspectElement.getChildren("scope")) {
        ContainerUtil.addAllNotNull(levels, s.getAttributeValue("level"));
      }
    }
    for (Iterator<String> iterator = levels.iterator(); iterator.hasNext(); ) {
      String level = iterator.next();
      if (profileManager.getOwnSeverityRegistrar().getSeverity(level) != null) {
        iterator.remove();
      }
    }
    if (!levels.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        if (Messages.showYesNoDialog(project, "Undefined severities detected: " +
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

  private static String getProfileName(Element rootElement) {
    for (Element option : rootElement.getChildren("option")) {
      String optionName = option.getAttributeValue("name");
      if (optionName.equals("myName")) {
        return option.getAttributeValue("value");
      }
    }
    return null;
  }
}
