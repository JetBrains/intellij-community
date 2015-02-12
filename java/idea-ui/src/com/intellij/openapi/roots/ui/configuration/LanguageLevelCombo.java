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

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author ven
 */
@SuppressWarnings("unchecked")
public class LanguageLevelCombo extends ComboBox {

  /** Default from current SDK */
  @Nullable
  private LanguageLevel myDefaultLevel;
  private Pair<String, String> myProjectDefault;

  public LanguageLevelCombo() {
    for (LanguageLevel level : LanguageLevel.values()) {
      addItem(level);
    }
    setRenderer(new ColoredListCellRendererWrapper() {
      @Override
      protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LanguageLevel) {
          append(((LanguageLevel)value).getPresentableText());
        }
        else if (value instanceof Pair) {
          Pair<String, String> pair = (Pair<String, String>)value;
          append(pair.first);
          if (pair.second != null) {
            append(" (" + pair.second + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }
    });
  }

  public void reset(Project project) {
    removeAllItems();
    for (LanguageLevel level : LanguageLevel.values()) {
      addItem(level);
    }
    myDefaultLevel = null;
    Sdk sdk = ProjectRootManagerEx.getInstanceEx(project).getProjectSdk();
    if (sdk != null) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
      if (version != null) {
        myDefaultLevel = version.getMaxLanguageLevel();
      }
    }
    Pair<String, String> item = null;
    if (myDefaultLevel != null) {
      item = Pair.create(JavaCoreBundle.message("default.language.level.description"), myDefaultLevel.getPresentableText());
      addItem(item);
    }
    else if (project.isDefault()) {
      item = Pair.create(JavaCoreBundle.message("default.language.level.description"), null);
      addItem(item);
      myDefaultLevel = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
    }

    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
    if (item != null && extension.isDefault()) {
      setSelectedItem(item);
    }
    else {
      setSelectedItem(extension.getLanguageLevel());
    }
  }

  @Nullable
  public LanguageLevel getSelectedLevel() {
    Object item = getSelectedItem();
    return item instanceof LanguageLevel ? (LanguageLevel)item : myDefaultLevel;
  }

  public boolean isDefault() {
    return !(getSelectedItem() instanceof LanguageLevel);
  }

  @Override
  public void setSelectedItem(Object anObject) {
    super.setSelectedItem(anObject == null ? myProjectDefault : anObject);
  }

  void addProjectDefault(String projectLevel) {
    myProjectDefault = Pair.create(ProjectBundle.message("project.language.level.combo.item"), projectLevel);
    insertItemAt(myProjectDefault, 0);
  }
}
