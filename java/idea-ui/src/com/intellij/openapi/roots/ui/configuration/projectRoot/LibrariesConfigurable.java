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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.PanelWithText;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class LibrariesConfigurable extends NamedConfigurable <String> {
  private static final Icon ICON = IconLoader.getIcon("/modules/library.png");
  
  private LibraryTable myLibraryTable;

  private final PanelWithText myPanel = new PanelWithText();

  protected LibrariesConfigurable(final LibraryTable libraryTable) {
    myLibraryTable = libraryTable;
  }

  public void reset() {
    // nothing to implement
  }

  public final void apply() throws ConfigurationException {
  }

  public final void disposeUIResources() {
    myLibraryTable = null;
  }

  public final boolean isModified() {
    return false;
  }


  public JComponent createOptionsPanel() {
    myPanel.setText(myLibraryTable.getPresentation().getDescription());
    return myPanel;
  }


  public String getDisplayName() {
    return myLibraryTable.getPresentation().getDisplayName(true);
  }

  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";
  }

  public Icon getIcon() {
    return ICON;
  }

  public void setDisplayName(final String name) {
    //do nothing
  }

  public String getEditableObject() {
    return myLibraryTable.getTableLevel();
  }

  public String getBannerSlogan() {
    return getDisplayName();
  }

}
