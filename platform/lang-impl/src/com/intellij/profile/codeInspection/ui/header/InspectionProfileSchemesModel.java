// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.header;

import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class InspectionProfileSchemesModel implements SchemesModel<InspectionProfileModifiableModel> {
  private final List<SingleInspectionProfilePanel> myProfilePanels = new ArrayList<>();
  private final List<InspectionProfileImpl> myDeletedProfiles = new SmartList<>();

  private final InspectionProfileManager myApplicationProfileManager;
  private final InspectionProfileManager myProjectProfileManager;

  protected InspectionProfileSchemesModel(@NotNull InspectionProfileManager appProfileManager,
                                          @NotNull InspectionProfileManager projectProfileManager) {
    myApplicationProfileManager = appProfileManager;
    myProjectProfileManager = projectProfileManager;
  }

  @Override
  public boolean canDuplicateScheme(@NotNull InspectionProfileModifiableModel profile) {
    return true;
  }

  @Override
  public boolean canResetScheme(@NotNull InspectionProfileModifiableModel profile) {
    return true;
  }

  @Override
  public boolean canDeleteScheme(@NotNull InspectionProfileModifiableModel candidateToDelete) {
    boolean projectProfileFound = false;
    boolean ideProfileFound = false;

    for (SingleInspectionProfilePanel profilePanel : myProfilePanels) {
      final InspectionProfileModifiableModel profile = profilePanel.getProfile();
      if (candidateToDelete == profile) continue;
      final boolean isProjectProfile = profile.getProfileManager() == myProjectProfileManager;
      projectProfileFound |= isProjectProfile;
      ideProfileFound |= !isProjectProfile;

      if (ideProfileFound && projectProfileFound) break;
    }

    return candidateToDelete.getProfileManager() == myProjectProfileManager ? projectProfileFound : ideProfileFound;
  }

  @Override
  public boolean isProjectScheme(@NotNull InspectionProfileModifiableModel profile) {
    return profile.isProjectLevel();
  }

  @Override
  public boolean canRenameScheme(@NotNull InspectionProfileModifiableModel profile) {
    return true;
  }

  @Override
  public boolean containsScheme(@NotNull String name, boolean isProjectProfile) {
    return hasName(name, isProjectProfile);
  }

  @Override
  public boolean differsFromDefault(@NotNull InspectionProfileModifiableModel profile) {
    return getProfilePanel(profile).differsFromDefault();
  }

  @Override
  public void removeScheme(@NotNull InspectionProfileModifiableModel profile) {
    final SingleInspectionProfilePanel panel = getProfilePanel(profile);
    removeProfile(profile);
    myDeletedProfiles.add(profile);
    onProfileRemoved(panel);
  }

  protected abstract void onProfileRemoved(@NotNull SingleInspectionProfilePanel profilePanel);

  void addProfile(InspectionProfileModifiableModel profile) {
    myProfilePanels.add(createPanel(profile));
  }

  void removeProfile(InspectionProfileImpl profile) {
    for (SingleInspectionProfilePanel panel : myProfilePanels) {
      if (panel.getProfile().equals(profile)) {
        myProfilePanels.remove(panel);
        break;
      }
    }
  }

  void updatePanel(@NotNull InspectionProfileSchemesPanel panel) {
    final List<InspectionProfileModifiableModel> allProfiles = myProfilePanels.stream().map(p -> p.getProfile()).collect(Collectors.toList());
    panel.resetSchemes(allProfiles);
  }

  void apply(InspectionProfileModifiableModel selected, Consumer<? super InspectionProfileImpl> applyRootProfileAction) {
    for (InspectionProfileImpl profile : myDeletedProfiles) {
      profile.getProfileManager().deleteProfile(profile);
    }
    myDeletedProfiles.clear();

    SingleInspectionProfilePanel selectedPanel = getProfilePanel(selected);
    for (SingleInspectionProfilePanel panel : getProfilePanels()) {
      panel.apply();
      if (panel == selectedPanel) {
        applyRootProfileAction.consume(panel.getProfile());
      }
    }
  }

  void reset() {
    disposeUI();
    myDeletedProfiles.clear();
    getSortedProfiles(myApplicationProfileManager, myProjectProfileManager)
      .stream()
      .map(source -> {
        try {
          return new InspectionProfileModifiableModel(source);
        }
        catch (Exception e) {
          //noinspection ConstantConditions,InstanceofCatchParameter
          if (e instanceof JDOMException) {
            return null;
          } else {
            throw new RuntimeException(e);
          }
        }
      })
      .forEach(this::addProfile);
  }

  void disposeUI() {
    for (SingleInspectionProfilePanel panel : myProfilePanels) {
      panel.disposeUI();
    }
    myProfilePanels.clear();
  }

  public SingleInspectionProfilePanel getProfilePanel(InspectionProfileImpl profile) {
    return myProfilePanels.stream().filter(panel -> panel.getProfile().equals(profile)).findFirst().orElse(null);
  }

  protected abstract SingleInspectionProfilePanel createPanel(InspectionProfileModifiableModel model);

  boolean hasName(@NotNull final String name, boolean shared) {
    final boolean hasName = myProfilePanels.stream().map(SingleInspectionProfilePanel::getProfile).anyMatch(p -> name.equals(p.getName()) && p.isProjectLevel() == shared);
    if (hasName) return true;
    return myProfilePanels.stream().anyMatch(p -> {
      final InspectionProfileModifiableModel profile = p.getProfile();
      return name.equals(profile.getName()) && profile.isProjectLevel() == shared;
    });
  }

  List<SingleInspectionProfilePanel> getProfilePanels() {
    return myProfilePanels;
  }

  int getSize() {
    return myProfilePanels.size();
  }

  boolean hasDeletedProfiles() {
    return !myDeletedProfiles.isEmpty();
  }

  @NotNull
  InspectionProfileModifiableModel getModifiableModelFor(@NotNull InspectionProfileImpl profile) {
    if (profile instanceof InspectionProfileModifiableModel) {
      return (InspectionProfileModifiableModel)profile;
    }
    for (SingleInspectionProfilePanel panel : myProfilePanels) {
      final InspectionProfileModifiableModel modifiableModel = panel.getProfile();
      if (modifiableModel.getSource().equals(profile)) {
        return modifiableModel;
      }
    }
    throw new AssertionError("profile " + profile.getName() + " is not present among profile panels" +
                             Arrays.toString(myProfilePanels.stream().map(p -> p.getProfile().getName()).toArray(String[]::new)));
  }

  public static List<InspectionProfileImpl> getSortedProfiles(InspectionProfileManager appManager,
                                                              InspectionProfileManager projectManager) {
    return ContainerUtil.concat(ContainerUtil.sorted(appManager.getProfiles()),
                                ContainerUtil.sorted(projectManager.getProfiles()));
  }
}
