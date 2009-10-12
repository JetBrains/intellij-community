/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;

/**
 * @author ven
 */
public class LanguageLevelCombo extends ComboBox {
  public static final String USE_PROJECT_LANGUAGE_LEVEL = ProjectBundle.message("project.language.level.combo.item");

  public LanguageLevelCombo() {
    for (LanguageLevel level : LanguageLevel.values()) {
      addItem(level);
    }
    setRenderer(new MyDefaultListCellRenderer());
  }

  public void reset(Project project){
    setSelectedItem(LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
  }

  public void setSelectedItem(Object anObject) {
    if (anObject == null){
      anObject = USE_PROJECT_LANGUAGE_LEVEL;
    }
    super.setSelectedItem(anObject);
  }

  private static class MyDefaultListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof LanguageLevel) {
        setText(((LanguageLevel)value).getPresentableText());
      }
      else if (value instanceof String) {
        setText((String)value);
      }
      return this;
    }
  }
}
