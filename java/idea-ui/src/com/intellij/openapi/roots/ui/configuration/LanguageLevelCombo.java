// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * @author ven
 */
public abstract class LanguageLevelCombo extends ComboBoxWithSeparators<LanguageLevel> {
  private final static LanguageLevel[] LTS = {LanguageLevel.JDK_17, LanguageLevel.JDK_11, LanguageLevel.JDK_1_8};

  public LanguageLevelCombo(@Nls String defaultItem) {
    addItem(new DefaultEntry(defaultItem));

    addItem(new Separator(JavaUiBundle.message("language.level.combo.lts.versions")));

    for (LanguageLevel level : LTS) {
      addItem(new Entry(level));
    }

    addItem(new Separator(JavaUiBundle.message("language.level.combo.other.versions")));

    Arrays.stream(LanguageLevel.values())
      .sorted((l1, l2) -> l2.toJavaVersion().feature - l1.toJavaVersion().feature)
      .filter(level -> level != LanguageLevel.JDK_X && (level.isPreview() || !ArrayUtil.contains(level, LTS)))
      .forEach(level -> addItem(new Entry(level)));

    addItem(new Entry(LanguageLevel.JDK_X));
  }

  private class Entry extends ComboBoxWithSeparators<LanguageLevel>.EntryModel<LanguageLevel> {
    private Entry(@NotNull LanguageLevel myItem) {
      super(myItem);
    }

    @NotNull
    @Override
    public @NlsContexts.ListItem String getPresentableText() {
      final LanguageLevel item = getItem();
      assert item != null;
      return item.getPresentableText();
    }
  }

  private class DefaultEntry extends ComboBoxWithSeparators<LanguageLevel>.EntryModel<LanguageLevel> {
    @NlsContexts.ListItem private final String myText;

    private DefaultEntry(@NlsContexts.ListItem String text) {
      super(null);
      myText = text;
    }

    @Nullable
    @Override
    public LanguageLevel getItem() {
      return getDefaultLevel();
    }

    @NotNull
    @Override
    public @NlsContexts.ListItem String getPresentableText() {
      return myText;
    }

    @NotNull
    @Override
    public @NlsContexts.ListItem String getSecondaryText() {
      final LanguageLevel item = getItem();
      return item != null ? MessageFormat.format("({0})", item.getPresentableText()) : "";
    }
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
    if (selectedItem instanceof Entry) return ((Entry)selectedItem).getItem();
    if (selectedItem instanceof DefaultEntry) return getDefaultLevel();
    return null;
  }

  public boolean isDefault() {
    return getSelectedItem() instanceof DefaultEntry;
  }

  @Override
  public void setSelectedItem(Object anObject) {
    if (anObject instanceof ComboBoxWithSeparators<?>.Separator) return;
    final @NonNls Object levelToSelect = anObject == null ? "default" : anObject;
    if (levelToSelect instanceof Entry || levelToSelect instanceof DefaultEntry) {
      // Select from existing entry
      super.setSelectedItem(levelToSelect);
    } else {
      // Select from LanguageLevel or String (default level)
      super.setSelectedItem(getEntryForLevel(levelToSelect));
    }
    checkAcceptedLevel(getSelectedLevel());
  }

  private ComboBoxWithSeparators<LanguageLevel>.EntryModel<LanguageLevel> getEntryForLevel(Object levelToSelect) {
    for (int i = 0; i < getItemCount(); i++) {
      final EntryModel<LanguageLevel> entry = getItemAt(i);
      if (entry instanceof DefaultEntry && levelToSelect instanceof String) {
        return entry;
      }
      else if (entry instanceof Entry && entry.getItem() == levelToSelect) {
        return entry;
      }
    }
    return null;
  }
}