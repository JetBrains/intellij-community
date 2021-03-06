// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ven
 */
public abstract class LanguageLevelCombo extends ComboBox<Object> {
  private final @Nls String myDefaultItem;

  public LanguageLevelCombo(@Nls String defaultItem) {
    myDefaultItem = defaultItem;
    insertItemAt(myDefaultItem, 0);
    for (LanguageLevel level : LanguageLevel.values()) {
      addItem(level);
    }

    setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LanguageLevel) {
          append(((LanguageLevel)value).getPresentableText());
        }
        else if (value instanceof String) {  // default for SDK or project
          append((String)value);
          LanguageLevel defaultLevel = getDefaultLevel();
          if (defaultLevel != null) {
            append(" (" + defaultLevel.getPresentableText() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
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
    if (getSelectedItem() == myDefaultItem) {
      checkAcceptedLevel(newLevel);
    }
  }

  private void updateDefaultLevel(LanguageLevel newLevel, boolean isDefaultProject) {
    if (newLevel == null && !isDefaultProject) {
      if (getSelectedItem() == myDefaultItem) {
        setSelectedItem(getDefaultLevel());
      }
    }
    repaint();
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
    super.setSelectedItem(anObject == null || anObject == LanguageLevel.JDK_X ? myDefaultItem : anObject);
    checkAcceptedLevel(getSelectedLevel());
  }
}