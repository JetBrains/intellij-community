// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveComment;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public final class CodeCleanupOnSaveActionInfo extends ActionOnSaveInfoBase {
  private static final String CODE_CLEANUP_ON_SAVE_PROPERTY = "code.cleanup.on.save";
  private static final boolean CODE_CLEANUP_ON_SAVE_DEFAULT = false;
  private String myProfile;

  public static boolean isCodeCleanupOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(CODE_CLEANUP_ON_SAVE_PROPERTY, CODE_CLEANUP_ON_SAVE_DEFAULT);
  }

  public CodeCleanupOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context,
          CodeInsightBundle.message("actions.on.save.page.checkbox.run.code.cleanup"),
          CODE_CLEANUP_ON_SAVE_PROPERTY,
          CODE_CLEANUP_ON_SAVE_DEFAULT);
    myProfile = CodeCleanupOnSaveOptions.getInstance(getProject()).getProfile();
  }

  @Override
  public ActionOnSaveComment getComment() {
    return ActionOnSaveComment.info(CodeInsightBundle.message("actions.on.save.page.code.cleanup.comment"));
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    return List.of(createGoToPageInSettingsLink(CodeInsightBundle.message("actions.on.save.page.link.configure.inspections"),
                                                InspectionToolsConfigurable.ID));
  }

  private record ProfileOption(@NlsSafe @Nullable String profileName) {}

  @Override
  public @NotNull List<? extends DropDownLink<?>> getDropDownLinks() {
    List<ProfileOption> profiles = new ArrayList<>();

    // Project Profile
    profiles.add(new ProfileOption(null));

    // Stored in project
    List<ProfileOption> projectProfiles = ContainerUtil.map(
      InspectionProfileManager.getInstance(getProject()).getProfiles(),
      p -> new ProfileOption(p.getDisplayName())
    );
    profiles.addAll(projectProfiles);

    // Stored in IDE
    List<ProfileOption> ideProfiles = ContainerUtil.map(
      InspectionProfileManager.getInstance().getProfiles(),
      p -> new ProfileOption(p.getDisplayName())
    );
    profiles.addAll(ideProfiles);

    // Separators
    final var separators = new HashMap<ProfileOption, String>();
    if (!projectProfiles.isEmpty()) {
      separators.put(projectProfiles.get(0), IdeBundle.message("separator.scheme.stored.in", IdeBundle.message("scheme.project")));
    }
    if (!ideProfiles.isEmpty()) {
      separators.put(ideProfiles.get(0), IdeBundle.message("separator.scheme.stored.in", IdeBundle.message("scheme.ide")));
    }

    var selectedOption = getSelectedProfile(profiles);

    DropDownLink<ProfileOption> inspectionProfileLink = new DropDownLink<>(
      selectedOption,
      profiles,
      option -> {
        myProfile = option.profileName;
      },
      true) {

      @Override
      protected @Nls @NotNull String itemToString(ProfileOption item) {
        if (item.profileName == null) return CodeInsightBundle.message("actions.on.save.page.code.cleanup.project.profile");
        return StringUtil.shortenTextWithEllipsis(
          CodeInsightBundle.message("actions.on.save.page.code.cleanup.profile", item.profileName), 40, 0
        );
      }

      @Override
      public @NotNull ListCellRenderer<? super ProfileOption> createRenderer() {
        return BuilderKt.groupedTextListCellRenderer(p -> {
          if (p.profileName == null) return CodeInsightBundle.message("actions.on.save.page.code.cleanup.project.profile");
          return p.profileName();
        }, p -> separators.getOrDefault(p, null));
      }
    };

    return List.of(inspectionProfileLink);
  }

  private ProfileOption getSelectedProfile(List<ProfileOption> profiles) {
    if (myProfile == null) return profiles.get(0);
    final var selected = ContainerUtil.find(profiles, p -> p != null && Objects.equals(p.profileName, myProfile));
    if (selected == null) return profiles.get(0); // If the profile has been deleted, default to project profile
    return selected;
  }

  @Override
  protected boolean isModified() {
    return super.isModified() || !Objects.equals(myProfile, CodeCleanupOnSaveOptions.getInstance(getProject()).getProfile());
  }

  @Override
  protected void apply() {
    super.apply();
    CodeCleanupOnSaveOptions.getInstance(getProject()).setProfile(myProfile);
  }
}