// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;

public final class FileColorManagerImpl extends FileColorManager {
  private static final String FC_ENABLED = "FileColorsEnabled";
  private static final String FC_TABS_ENABLED = "FileColorsForTabsEnabled";
  private static final String FC_PROJECT_VIEW_ENABLED = "FileColorsForProjectViewEnabled";

  private final Project myProject;
  private final FileColorsModel myModel;

  private final NotNullLazyValue<FileColorsModel> myInitializedModel;

  private static final Map<String, Color> ourDefaultColors = Map.of(
    "Blue", JBColor.namedColor("FileColor.Blue", new JBColor(0xeaf6ff, 0x4f556b)),
    "Green", JBColor.namedColor("FileColor.Green", new JBColor(0xeffae7, 0x49544a)),
    "Orange", JBColor.namedColor("FileColor.Orange", new JBColor(0xf6e9dc, 0x806052)),
    "Rose", JBColor.namedColor("FileColor.Rose", new JBColor(0xf2dcda, 0x6e535b)),
    "Violet", JBColor.namedColor("FileColor.Violet", new JBColor(0xe6e0f1, 0x534a57)),
    "Yellow", JBColor.namedColor("FileColor.Yellow", new JBColor(0xffffe4, 0x4f4b41)),
    "Gray", JBColor.namedColor("FileColor.Gray", new JBColor(0xf5f5f5, 0x45484a))
    );

  public FileColorManagerImpl(@NotNull Project project) {
    myProject = project;
    myModel = new FileColorsModel(project);

    myInitializedModel = NotNullLazyValue.createValue(() -> {
      project.getService(PerTeamFileColorModelStorageManager.class);
      project.getService(PerUserFileColorModelStorageManager.class);
      return myModel;
    });
  }

  @Override
  public boolean isEnabled() {
    return _isEnabled();
  }

  public static boolean _isEnabled() {
    return PropertiesComponent.getInstance().getBoolean(FC_ENABLED, true);
  }

  @Override
  public void setEnabled(boolean enabled) {
    PropertiesComponent.getInstance().setValue(FC_ENABLED, enabled, true);
  }

  @ApiStatus.Internal
  public static void setEnabledForTabs(boolean enabled) {
    PropertiesComponent.getInstance().setValue(FC_TABS_ENABLED, Boolean.toString(enabled));
  }

  @Override
  public boolean isEnabledForTabs() {
    return _isEnabledForTabs();
  }

  @ApiStatus.Internal
  public static boolean _isEnabledForTabs() {
    return PropertiesComponent.getInstance().getBoolean(FC_TABS_ENABLED, true);
  }

  @Override
  public boolean isEnabledForProjectView() {
    return _isEnabledForProjectView();
  }

  public static boolean _isEnabledForProjectView() {
    return PropertiesComponent.getInstance().getBoolean(FC_PROJECT_VIEW_ENABLED, true);
  }

  public static void setEnabledForProjectView(boolean enabled) {
    PropertiesComponent.getInstance().setValue(FC_PROJECT_VIEW_ENABLED, Boolean.toString(enabled));
  }

  @Override
  public @Nullable Color getColor(@NotNull @NonNls String id) {
    Color color = ourDefaultColors.get(id);
    return color == null ? ColorUtil.fromHex(id, null) : color;
  }

  @Override
  public @NotNull @Nls String getColorName(@NotNull @NonNls String id) {
    return ourDefaultColors.containsKey(id) ?
           IdeBundle.message("color.name." + id.toLowerCase(Locale.ENGLISH)) :
           IdeBundle.message("settings.file.color.custom.name");
  }

  @Override
  public Collection<@NonNls String> getColorIDs() {
    return ContainerUtil.sorted(ourDefaultColors.keySet());
  }

  @Override
  public Collection<@Nls String> getColorNames() {
    List<String> list = new ArrayList<>(ourDefaultColors.size());
    for (String key : ourDefaultColors.keySet()) {
      list.add(IdeBundle.message("color.name." + key.toLowerCase(Locale.ENGLISH)));
    }
    list.sort(null);
    return list;
  }

  @Override
  public @Nullable Color getRendererBackground(VirtualFile vFile) {
    if (vFile == null) return null;

    if (isEnabled()) {
      final Color fileColor = getFileColor(vFile);
      if (fileColor != null) return fileColor;
    }

    //return FileEditorManager.getInstance(myProject).isFileOpen(vFile) && !UIUtil.isUnderDarcula() ? LightColors.SLIGHTLY_GREEN : null;
    return null;
  }

  @Override
  public @Nullable Color getRendererBackground(PsiFile file) {
    if (file == null) return null;

    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return null;

    return getRendererBackground(vFile);
  }

  @Override
  public void addScopeColor(@NotNull String scopeName, @NotNull String colorName, boolean isProjectLevel) {
    myInitializedModel.getValue().add(scopeName, colorName, isProjectLevel);
  }

  @Override
  public @Nullable Color getFileColor(@NotNull VirtualFile file) {
    if (!isEnabled()) return null;
    String colorName = myInitializedModel.getValue().getColor(file, getProject());
    return colorName == null ? null : getColor(colorName);
  }

  @Override
  public @Nullable Color getScopeColor(@NotNull String scopeName) {
    if (!isEnabled()) {
      return null;
    }
    String colorName = myInitializedModel.getValue().getScopeColor(scopeName, getProject());
    return colorName == null ? null : getColor(colorName);
  }

  @Override
  public boolean isShared(@NotNull String scopeName) {
    return myInitializedModel.getValue().isProjectLevel(scopeName);
  }

  @NotNull
  FileColorsModel getModel() {
    return myInitializedModel.getValue();
  }

  @NotNull
  FileColorsModel getUninitializedModel() {
    return myModel;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  public @NotNull List<FileColorConfiguration> getApplicationLevelConfigurations() {
    return myInitializedModel.getValue().getLocalConfigurations();
  }

  public List<FileColorConfiguration> getProjectLevelConfigurations() {
    return myInitializedModel.getValue().getProjectLevelConfigurations();
  }

  public static @Nullable @NonNls String getColorID(@NotNull Color color) {
    for (Map.Entry<String, Color> entry : ourDefaultColors.entrySet()) {
      if (color.equals(entry.getValue())) {
        return entry.getKey();
      }
    }
    return null;
  }

  static @Nls String getAlias(@Nls String text) {
    return StartupUiUtil.isUnderDarcula() && text.equals(IdeBundle.message("color.name.yellow")) ?
           IdeBundle.message("color.name.brown") : text;
  }
}
