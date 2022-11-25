// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.GroupedComboBoxRenderer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.Arrays;

/**
 * @author ven
 */
public abstract class LanguageLevelCombo extends ComboBox<Object> {
  private final static LanguageLevel[] LTS = {LanguageLevel.JDK_17, LanguageLevel.JDK_11, LanguageLevel.JDK_1_8};

  public LanguageLevelCombo(@Nls String defaultItem) {
    addItem(defaultItem);

    addItem(new ListSeparator(JavaUiBundle.message("language.level.combo.lts.versions")));

    for (LanguageLevel level : LTS) {
      addItem(level);
    }

    addItem(new ListSeparator(JavaUiBundle.message("language.level.combo.other.versions")));

    LanguageLevel highestPreviewLevel = LanguageLevel.HIGHEST.getPreviewLevel();
    LanguageLevel highestWithPreview = highestPreviewLevel != null ? highestPreviewLevel : LanguageLevel.HIGHEST;
    Arrays.stream(LanguageLevel.values())
      .sorted((l1, l2) -> l2.toJavaVersion().feature - l1.toJavaVersion().feature)
      .filter(level -> level.compareTo(highestWithPreview) <= 0 && (level.isPreview() || !ArrayUtil.contains(level, LTS)))
      .forEach(level -> addItem(level));

    addItem(new ListSeparator(JavaUiBundle.message("language.level.combo.experimental.versions")));
    for (LanguageLevel level : LanguageLevel.values()) {
      if (level.compareTo(highestWithPreview) > 0) {
        addItem(level);
      }
    }

    setSwingPopup(false);
    setRenderer(new GroupedComboBoxRenderer<>(this) {
      @Override
      public @NotNull String getText(Object value) {
        if (value instanceof LanguageLevel level) {
          return level.getPresentableText();
        } else if (value instanceof @NlsContexts.ListItem String s) {
          return s;
        }
        return "";
      }

      @Nullable
      @Override
      public String getSecondaryText(Object value) {
        if (value instanceof String) {
          final LanguageLevel defaultLevel = getDefaultLevel();
          return defaultLevel != null ? MessageFormat.format("({0})", defaultLevel.getPresentableText())
                                      : "";
        }
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(Object value) {
        return null;
      }
    });
  }

  private void checkAcceptedLevel(LanguageLevel selectedLevel) {
    if (selectedLevel == null)
      return;
    hidePopup();
    LanguageLevel level = AcceptedLanguageLevelsSettings.checkAccepted(this, selectedLevel);
    if (level == null) {
      setSelectedItem(AcceptedLanguageLevelsSettings.getHighestAcceptedLevel());
    }
  }

  public void reset(@NotNull Project project) {
    Sdk sdk = ProjectRootManagerEx.getInstanceEx(project).getProjectSdk();
    sdkUpdated(sdk, project.isDefault());

    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
    if (extension.isDefault()) {
      setSelectedItem("default");
    }
    else {
      setSelectedItem(extension.getLanguageLevel());
    }
  }

  protected abstract LanguageLevel getDefaultLevel();

  void sdkUpdated(Sdk sdk, boolean isDefaultProject) {
    LanguageLevel newLevel = null;
    if (sdk != null) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
      if (version != null) {
        newLevel = version.getMaxLanguageLevel();
      }
    }
    updateDefaultLevel(newLevel, isDefaultProject);
    if (isDefault()) {
      checkAcceptedLevel(newLevel);
    }
  }

  private void updateDefaultLevel(LanguageLevel newLevel, boolean isDefaultProject) {
    if (newLevel == null && !isDefaultProject) {
      if (isDefault()) {
        setSelectedItem(getDefaultLevel());
      }
    }
    repaint();
  }

  @Nullable
  public LanguageLevel getSelectedLevel() {
    final Object selectedItem = getSelectedItem();
    if (selectedItem instanceof LanguageLevel level) return level;
    if (selectedItem instanceof String) return getDefaultLevel();
    return null;
  }

  public boolean isDefault() {
    return getSelectedItem() instanceof String;
  }

  @Override
  public void setSelectedItem(Object anObject) {
    final @NonNls Object levelToSelect = anObject == null ? "default" : anObject;
    final Object entryForLevel = getEntryForLevel(levelToSelect);
    if (entryForLevel != null) super.setSelectedItem(entryForLevel);
    checkAcceptedLevel(getSelectedLevel());
  }

  private Object getEntryForLevel(Object levelToSelect) {
    for (int i = 0; i < getItemCount(); i++) {
      final Object entry = getItemAt(i);
      if (levelToSelect == entry) return entry;
      if (levelToSelect instanceof String && entry instanceof String) {
        return entry;
      }
    }
    return null;
  }
}