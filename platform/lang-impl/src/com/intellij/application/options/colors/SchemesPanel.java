/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.application.options.colors;

import com.intellij.application.options.SkipSelfSearchComponent;
import com.intellij.application.options.schemes.AbstractSchemesPanel;
import com.intellij.application.options.schemes.DefaultSchemeActions;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SchemesPanel extends AbstractSchemesPanel<EditorColorsScheme> implements SkipSelfSearchComponent {
  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public SchemesPanel(ColorAndFontOptions options) {
    myOptions = options;
  }

  private boolean myListLoaded = false;

  public boolean areSchemesLoaded() {
    return myListLoaded;
  }


  @Deprecated
  public boolean updateDescription(boolean modified) {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    if (modified && ColorAndFontOptions.isReadOnly(scheme)) {
      return false;
    }

    return true;
  }

  public void resetSchemesCombo(final Object source) {
    if (this != source) {
      setListLoaded(false);

      EditorColorsScheme selectedSchemeBackup = myOptions.getSelectedScheme();
      getSchemesCombo().removeAllItems();

      String[] schemeNames = myOptions.getSchemeNames();
      MySchemeItem itemToSelect = null;
      for (String schemeName : schemeNames) {
        EditorColorsScheme scheme = myOptions.getScheme(schemeName); 
        MySchemeItem item = new MySchemeItem(scheme);
        if (scheme == selectedSchemeBackup) itemToSelect = item;
        getSchemesCombo().addItem(item);
      }

      getSchemesCombo().setSelectedItem(itemToSelect);
      setListLoaded(true);

      myDispatcher.getMulticaster().schemeChanged(this);
    }
  }
  
  @Nullable
  private String getSelectedSchemeName() {
    return getSchemesCombo().getSelectedIndex() != -1 ? ((MySchemeItem)getSchemesCombo().getSelectedItem()).getSchemeName() : null;
  }

  private void setListLoaded(final boolean b) {
    myListLoaded = b;
  }

  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  protected ComboBox createSchemesCombo() {
    ComboBox schemesCombo = new ComboBox();
    schemesCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        String selectedName = getSelectedSchemeName();
        if (selectedName != null) {
          myOptions.selectScheme(selectedName);
          if (areSchemesLoaded()) {
            myDispatcher.getMulticaster().schemeChanged(SchemesPanel.this);
          }
        }
      }
    });
    return schemesCombo;
  }

  @Override
  protected DefaultSchemeActions<EditorColorsScheme> createSchemeActions() {
    return
      new ColorSchemeActions() {
      @NotNull
      @Override
      protected JComponent getParentComponent() {
        return getToolbarPanel();
      }

      @NotNull
      @Override
      protected ColorAndFontOptions getOptions() {
        return myOptions;
      }

      @Nullable
      @Override
      protected EditorColorsScheme getCurrentScheme() {
        return myOptions != null ? myOptions.getScheme(getSelectedSchemeName()) : null;
      }
    };
  }

  private final static class MySchemeItem {
    private EditorColorsScheme myScheme;

    public MySchemeItem(EditorColorsScheme scheme) {
      myScheme = scheme;
    }
    
    public String getSchemeName() {
      return myScheme.getName();
    }

    @Override
    public String toString() {
      return AbstractColorsScheme.getDisplayName(myScheme);
    }
  }
  
}
