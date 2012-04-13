package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author ksafonov
 */
public abstract class HeaderHidingTabbedModuleEditor extends TabbedModuleEditor {

  public HeaderHidingTabbedModuleEditor(Project project, ModulesProvider modulesProvider, @NotNull Module module) {
    super(project, modulesProvider, module);
  }

  @Override
  protected JComponent createCenterPanel() {
    ModuleConfigurationEditor singleEditor = getSingleEditor();
    if (singleEditor != null) {
      final JComponent component = singleEditor.createComponent();
      singleEditor.reset();
      return component;
    }
    else {
      return super.createCenterPanel();
    }
  }

  @Nullable
  private ModuleConfigurationEditor getSingleEditor() {
    return myEditors.size() == 1 ? myEditors.get(0) : null;
  }

  @Override
  public ModuleConfigurationEditor getSelectedEditor() {
    ModuleConfigurationEditor singleEditor = getSingleEditor();
    return singleEditor != null ? singleEditor : super.getSelectedEditor();
  }

  @Override
  public void selectEditor(String displayName) {
    if (displayName != null) {
      ModuleConfigurationEditor singleEditor = getSingleEditor();
      if (singleEditor != null) {
        // TODO [ksafonov] commented until IDEA-73889 is implemented
        //assert singleEditor.getDisplayName().equals(displayName);
      }
      else {
        super.selectEditor(displayName);
      }
    }
  }

  @Override
  protected void restoreSelectedEditor() {
    ModuleConfigurationEditor singleEditor = getSingleEditor();
    if (singleEditor == null) {
      super.restoreSelectedEditor();
    }
  }

  @Override
  @Nullable
  public ModuleConfigurationEditor getEditor(@NotNull String displayName) {
    ModuleConfigurationEditor singleEditor = getSingleEditor();
    if (singleEditor != null) {
      if (displayName.equals(singleEditor.getDisplayName())) {
        return singleEditor;
      }
      else {
        return null;
      }
    }
    else {
      return super.getEditor(displayName);
    }
  }

  @Override
  protected void disposeCenterPanel() {
    if (getSingleEditor() == null) {
      super.disposeCenterPanel();
    }
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    ModuleConfigurationEditor singleEditor = getSingleEditor();
    if (singleEditor != null) {
      place.putPath(SELECTED_EDITOR_NAME, singleEditor.getDisplayName());
    }
    else {
      super.queryPlace(place);
    }
  }
}
