/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;

/**
 * @author ksafonov
 */
public abstract class TabbedModuleEditor extends ModuleEditor {

  private static final String SELECTED_EDITOR_KEY = TabbedModuleEditor.class.getName() + ".selectedEditor";

  private TabbedPaneWrapper myTabbedPane;

  public TabbedModuleEditor(Project project, ModulesProvider modulesProvider, @NotNull Module module) {
    super(project, modulesProvider, module);
  }

  private static String getSavedSelectedEditor() {
    return PropertiesComponent.getInstance().getValue(SELECTED_EDITOR_KEY);
  }

  private void saveSelectedEditor() {
    final String selectedTabName = getSelectedTabName();
    if (selectedTabName != null) {
      // already disposed
      PropertiesComponent.getInstance().setValue(SELECTED_EDITOR_KEY, selectedTabName);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    myTabbedPane = new TabbedPaneWrapper(this);

    for (ModuleConfigurationEditor editor : myEditors) {
      myTabbedPane.addTab(editor.getDisplayName(), editor.createComponent());
      editor.reset();
    }
    restoreSelectedEditor();

    myTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        saveSelectedEditor();
        if (myHistory != null) {
          myHistory.pushQueryPlace();
        }
      }
    });
    return myTabbedPane.getComponent();
  }

  @Override
  protected void restoreSelectedEditor() {
    selectEditor(getSavedSelectedEditor());
  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    if (place != null) {
      selectEditor((String)place.getPath(SELECTED_EDITOR_NAME));
    }
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull final Place place) {
    place.putPath(SELECTED_EDITOR_NAME, getSavedSelectedEditor());
  }

  @Nullable
  private String getSelectedTabName() {
    return myTabbedPane == null || myTabbedPane.getSelectedIndex() == -1 ? null : myTabbedPane.getTitleAt(myTabbedPane.getSelectedIndex());
  }

  @Override
  public void selectEditor(@Nullable String name) {
    if (name != null) {
      getPanel();
      final int editorTabIndex = getEditorTabIndex(name);
      if (editorTabIndex >= 0 && editorTabIndex < myTabbedPane.getTabCount()) {
        myTabbedPane.setSelectedIndex(editorTabIndex);
        saveSelectedEditor();
      }
    }
  }

  private int getEditorTabIndex(final String editorName) {
    if (myTabbedPane != null && editorName != null) {
      final int tabCount = myTabbedPane.getTabCount();
      for (int idx = 0; idx < tabCount; idx++) {
        if (editorName.equals(myTabbedPane.getTitleAt(idx))) {
          return idx;
        }
      }
    }
    return -1;
  }

  @Override
  @Nullable
  public ModuleConfigurationEditor getEditor(@NotNull String displayName) {
    int index = getEditorTabIndex(displayName);
    if (0 <= index && index < myEditors.size()) {
      return myEditors.get(index);
    }
    return null;
  }

  @Override
  public ModuleConfigurationEditor getSelectedEditor() {
    if (myTabbedPane == null) {
      return null;
    }

    String title = myTabbedPane.getSelectedTitle();
    if (title == null) {
      return null;
    }

    return getEditor(title);
  }

  @Override
  protected void disposeCenterPanel() {
    if (myTabbedPane != null) {
      saveSelectedEditor();
      myTabbedPane = null;
    }
  }
}
