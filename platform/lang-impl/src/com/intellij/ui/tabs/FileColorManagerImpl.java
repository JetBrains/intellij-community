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
    ourDefaultDarkColors.put("Green", new Color(0x253B10));
    ourDefaultDarkColors.put("Orange", new Color(0xB85E3A));
    ourDefaultDarkColors.put("Rose", new Color(0x4B193E));
    ourDefaultDarkColors.put("Violet", new Color(0x341657));
    ourDefaultDarkColors.put("Yellow", new Color(0x402D10));
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

  public boolean isEnabled() {
    return _isEnabled();
  }

  public static boolean _isEnabled() {
    return PropertiesComponent.getInstance().getBoolean(FC_ENABLED, true);
  }

  public void setEnabled(boolean enabled) {
    PropertiesComponent.getInstance().setValue(FC_ENABLED, Boolean.toString(enabled));
  }

  public void setEnabledForTabs(boolean enabled) {
    PropertiesComponent.getInstance().setValue(FC_TABS_ENABLED, Boolean.toString(enabled));
  }

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
    //if (!shared) {
    //  element.setAttribute("enabled", Boolean.toString(myEnabled));
    //  element.setAttribute("enabledForTabs", Boolean.toString(myEnabledForTabs));
    //}

    myModel.save(element, shared);
    //if (!shared) {
    //  final boolean exists = findConfigurationByName(NonProjectFilesScope.NAME, myModel.getLocalConfigurations()) != null;
    //  if (myEnabledForNonProject && !exists) {
    //    myEnabledForNonProject = false;
    //  } else if (!myEnabledForNonProject && exists) {
    //    myEnabledForNonProject = true;
    //  }
    //
    //  element.setAttribute("showNonProject", Boolean.toString(myEnabledForNonProject));
    //}

    return element;
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  public Color getColor(@NotNull final String name) {
    final Color color = UIUtil.isUnderDarcula() ? ourDefaultDarkColors.get(name) : ourDefaultColors.get(name);
    return color == null ? ColorUtil.fromHex(name, null) : color;
  }

  public Element getState() {
    initSharedConfigurations();
    return getState(false);
  }

  @SuppressWarnings({"AutoUnboxing"})
  void loadState(Element state, final boolean shared) {
    if (!shared) {
      //final String enabled = state.getAttributeValue("enabled");
      //myEnabled = enabled == null ? true : Boolean.valueOf(enabled);
      //
      //final String enabledForTabs = state.getAttributeValue("enabledForTabs");
      //myEnabledForTabs = enabledForTabs == null ? true : Boolean.valueOf(enabledForTabs);

      //final String showNonProject = state.getAttributeValue("showNonProject");
      //myEnabledForNonProject = showNonProject == null ? true : Boolean.valueOf(showNonProject);
    }

    myModel.load(state, shared);
    //final List<FileColorConfiguration> local = myModel.getLocalConfigurations();
    //if (!shared && myEnabledForNonProject && findConfigurationByName(NonProjectFilesScope.NAME, local) == null) {
    //  local.add(new FileColorConfiguration(NonProjectFilesScope.NAME, NonProjectFilesScope.DEFAULT_COLOR));
    //}
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  public Collection<String> getColorNames() {
    final Set<String> names = ourDefaultColors.keySet();
    final List<String> sorted = new ArrayList<String>(names);
    Collections.sort(sorted);
    return sorted;
  }

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
  public Color getRendererBackground(VirtualFile file) {
    return getRendererBackground(PsiManager.getInstance(myProject).findFile(file));
  }

  @Nullable
  @Override
  public Color getRendererBackground(PsiFile file) {
    if (file == null) return null;

    if (isEnabled()) {
      final Color fileColor = getFileColor(file);
      if (fileColor != null) return fileColor;
    }

    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return null;

    //todo[kb] slightly_green for darcula
    return FileEditorManager.getInstance(myProject).isFileOpen(vFile) && !UIUtil.isUnderDarcula() ? LightColors.SLIGHTLY_GREEN : null;
  }

  @Nullable
  public Color getFileColor(@NotNull final PsiFile file) {
    initSharedConfigurations();

    final String colorName = myModel.getColor(file);
    return colorName == null ? null : getColor(colorName);
  }

  @Nullable
  public Color getFileColor(@NotNull final VirtualFile file) {
    initSharedConfigurations();
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    if (psiFile != null) {
      return getFileColor(psiFile);
    } else {
      final String colorName = myModel.getColor(file, getProject());
      return colorName == null ? null : getColor(colorName);
    }
  }

  public boolean isShared(@NotNull final String scopeName) {
    return myModel.isShared(scopeName);
  }

  FileColorsModel getModel() {
    return myModel;
  }

  boolean isShared(FileColorConfiguration configuration) {
    return myModel.isShared(configuration);
  }

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

  @Nullable
  private static FileColorConfiguration findConfigurationByName(String name, List<FileColorConfiguration> configurations) {
    for (FileColorConfiguration configuration : configurations) {
      if (name.equals(configuration.getScopeName())) {
        return configuration;
      }
    }
    return null;
  }
}
