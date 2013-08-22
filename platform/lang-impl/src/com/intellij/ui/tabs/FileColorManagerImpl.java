/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.ui.tabs;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.LightColors;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
@State(
  name = "FileColors",
  storages = {@Storage( file = StoragePathMacros.WORKSPACE_FILE)})
public class FileColorManagerImpl extends FileColorManager implements PersistentStateComponent<Element> {
  public static final String FC_ENABLED = "FileColorsEnabled";
  public static final String FC_TABS_ENABLED = "FileColorsForTabsEnabled";
  public static final String FC_PROJECT_VIEW_ENABLED = "FileColorsForProjectViewEnabled";
  private final Project myProject;
  private final FileColorsModel myModel;
  private FileColorSharedConfigurationManager mySharedConfigurationManager;

  private static final Map<String, Color> ourDefaultColors;
  private static final Map<String, Color> ourDefaultDarkColors;

  static {
    ourDefaultColors = new LinkedHashMap<String, Color>();
    ourDefaultColors.put("Blue", new Color(0xdcf0ff));
    ourDefaultColors.put("Green", new Color(231, 250, 219));
    ourDefaultColors.put("Orange", new Color(246, 224, 202));
    ourDefaultColors.put("Rose", new Color(242, 206, 202));
    ourDefaultColors.put("Violet", new Color(222, 213, 241));
    ourDefaultColors.put("Yellow", new Color(255, 255, 228));
    ourDefaultDarkColors = new LinkedHashMap<String, Color>();
    ourDefaultDarkColors.put("Blue", new Color(0x2B3557));
    ourDefaultDarkColors.put("Green", new Color(0x2A3B2C));
    ourDefaultDarkColors.put("Orange", new Color(0x823B1C));
    ourDefaultDarkColors.put("Rose", new Color(0x542F3A));
    ourDefaultDarkColors.put("Violet", new Color(0x4f4056));
    ourDefaultDarkColors.put("Yellow", new Color(0x494539));
  }

  public FileColorManagerImpl(@NotNull final Project project) {
    myProject = project;
    myModel = new FileColorsModel(project);
  }

  private void initSharedConfigurations() {
    if (mySharedConfigurationManager == null) {
      mySharedConfigurationManager = ServiceManager.getService(myProject, FileColorSharedConfigurationManager.class);
    }
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
    PropertiesComponent.getInstance().setValue(FC_ENABLED, Boolean.toString(enabled));
  }

  public void setEnabledForTabs(boolean enabled) {
    PropertiesComponent.getInstance().setValue(FC_TABS_ENABLED, Boolean.toString(enabled));
  }

  @Override
  public boolean isEnabledForTabs() {
    return _isEnabledForTabs();
  }

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

  public Element getState(final boolean shared) {
    Element element = new Element("state");
    myModel.save(element, shared);
    return element;
  }

  @Override
  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public Color getColor(@NotNull final String name) {
    final Color color = UIUtil.isUnderDarcula() ? ourDefaultDarkColors.get(name) : ourDefaultColors.get(name);
    return color == null ? ColorUtil.fromHex(name, null) : color;
  }

  @Override
  public Element getState() {
    initSharedConfigurations();
    return getState(false);
  }

  @SuppressWarnings({"AutoUnboxing"})
  void loadState(Element state, final boolean shared) {
    myModel.load(state, shared);
  }

  @Override
  @SuppressWarnings({"MethodMayBeStatic"})
  public Collection<String> getColorNames() {
    final Set<String> names = ourDefaultColors.keySet();
    final List<String> sorted = new ArrayList<String>(names);
    Collections.sort(sorted);
    return sorted;
  }

  @Override
  @SuppressWarnings({"AutoUnboxing"})
  public void loadState(Element state) {
    initSharedConfigurations();
    loadState(state, false);
  }

  @Override
  public boolean isColored(@NotNull final String scopeName, final boolean shared) {
    return myModel.isColored(scopeName, shared);
  }

  @Nullable
  @Override
  public Color getRendererBackground(VirtualFile vFile) {
    if (vFile == null) return null;

    if (isEnabled()) {
      final Color fileColor = getFileColor(vFile);
      if (fileColor != null) return fileColor;
    }

    //todo[kb] slightly_green for darcula
    return FileEditorManager.getInstance(myProject).isFileOpen(vFile) && !UIUtil.isUnderDarcula() ? LightColors.SLIGHTLY_GREEN : null;
  }

  @Nullable
  @Override
  public Color getRendererBackground(PsiFile file) {
    if (file == null) return null;

    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return null;

    return getRendererBackground(vFile);
  }

  @Override
  @Nullable
  public Color getFileColor(@NotNull final PsiFile file) {
    initSharedConfigurations();

    final String colorName = myModel.getColor(file);
    return colorName == null ? null : getColor(colorName);
  }

  @Override
  @Nullable
  public Color getFileColor(@NotNull final VirtualFile file) {
    initSharedConfigurations();

    final String colorName = myModel.getColor(file, getProject());
    return colorName == null ? null : getColor(colorName);
  }

  @Override
  public boolean isShared(@NotNull final String scopeName) {
    return myModel.isShared(scopeName);
  }

  FileColorsModel getModel() {
    return myModel;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  public List<FileColorConfiguration> getLocalConfigurations() {
    return myModel.getLocalConfigurations();
  }

  public List<FileColorConfiguration> getSharedConfigurations() {
    return myModel.getSharedConfigurations();
  }

  @Nullable
  public static String getColorName(Color color) {
    for (String name : ourDefaultColors.keySet()) {
      if (color.equals(ourDefaultColors.get(name))) {
        return name;
      }
    }
    return null;
  }

  static String getAlias(String text) {
    if (UIUtil.isUnderDarcula()) {
      if (text.equals("Yellow")) return "Brown";
    }
    return text;
  }

}
