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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ven
 */
@SuppressWarnings("unchecked")
public abstract class LanguageLevelCombo extends ComboBox {

  private final String myDefaultItem;

  public LanguageLevelCombo(String defaultItem) {
    myDefaultItem = defaultItem;
    for (LanguageLevel level : LanguageLevel.values()) {
      addItem(level);
    }
    setRenderer(new ColoredListCellRendererWrapper() {
      @Override
      protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LanguageLevel) {
          append(((LanguageLevel)value).getPresentableText());
        }
        else if (value instanceof String) {    // default for SDK or project
          append((String)value);
          LanguageLevel defaultLevel = getDefaultLevel();
          if (defaultLevel != null) {
            append(" (" + defaultLevel.getPresentableText() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }
    });
  }

  public void reset(@NotNull Project project) {
    removeAllItems();
    for (LanguageLevel level : LanguageLevel.values()) {
      addItem(level);
    }
    Sdk sdk = ProjectRootManagerEx.getInstanceEx(project).getProjectSdk();
    sdkUpdated(sdk, project.isDefault());

    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
    if (extension.isDefault()) {
      setSelectedItem(myDefaultItem);
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
  }

  private void updateDefaultLevel(LanguageLevel newLevel, boolean isDefaultProject) {
    if (newLevel == null && !isDefaultProject) {
      if (getSelectedItem() == myDefaultItem) {
        setSelectedItem(getDefaultLevel());
      }
      removeItem(myDefaultItem);
    }
    else if (!(getItemAt(0) instanceof String)) {
      addDefaultItem();
      setSelectedIndex(0);
    }
    repaint();
  }

  void addDefaultItem() {
    insertItemAt(myDefaultItem, 0);
  }

  public LanguageLevel getSelectedLevel() {
    Object item = getSelectedItem();
    return item instanceof LanguageLevel ? (LanguageLevel)item : getDefaultLevel();
  }

  public boolean isDefault() {
    return !(getSelectedItem() instanceof LanguageLevel);
  }

  @Override
  public void setSelectedItem(Object anObject) {
    super.setSelectedItem(anObject == null ? myDefaultItem : anObject);
  }
}
