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

package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.Map;

public class FileEncodingConfigurable implements SearchableConfigurable, NonDefaultProjectConfigurable, OptionalConfigurable {
  private final Project myProject;
  private FileTreeTable myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JCheckBox myAutodetectUTFEncodedFilesCheckBox;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private Charset mySelectedCharsetForPropertiesFiles;
  private JComboBox myIdeEncodingsCombo;
  private ChooseFileEncodingAction myAction;
  private static final String SYSTEM_DEFAULT = IdeBundle.message("encoding.name.system.default");

  public static FileEncodingConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, FileEncodingConfigurable.class);
  }

  public FileEncodingConfigurable(Project project) {
    myProject = project;
  }

  @Nls
  public String getDisplayName() {
    return IdeBundle.message("file.encodings.configurable");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configureEncoding.png");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.file.encodings";
  }

  public String getId() {
    return "File.Encoding";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public JComponent createComponent() {
    myAction = new ChooseFileEncodingAction(null) {
      public void update(final AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(mySelectedCharsetForPropertiesFiles == null ? SYSTEM_DEFAULT :
                                          mySelectedCharsetForPropertiesFiles.displayName());
      }

      protected void chosen(final VirtualFile virtualFile, final Charset charset) {
        mySelectedCharsetForPropertiesFiles = charset == NO_ENCODING ? null : charset;
        update(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", myAction.getTemplatePresentation(),
                                          ActionManager.getInstance(), 0));
      }
    };
    myPropertiesFilesEncodingCombo.removeAll();
    Presentation templatePresentation = myAction.getTemplatePresentation();
    myPropertiesFilesEncodingCombo.add(myAction.createCustomComponent(templatePresentation), BorderLayout.CENTER);
    myTreeView = new FileTreeTable(myProject);
    myTreePanel.setViewportView(myTreeView);
    return myPanel;
  }

  public boolean isModified() {
    if (isEncodingModified()) return true;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);

    Map<VirtualFile, Charset> editing = myTreeView.getValues();
    Map<VirtualFile, Charset> mapping = EncodingProjectManager.getInstance(myProject).getAllMappings();
    boolean same = editing.equals(mapping)
       && Comparing.equal(encodingManager.getDefaultCharsetForPropertiesFiles(null), mySelectedCharsetForPropertiesFiles)
       && encodingManager.isUseUTFGuessing(null) == myAutodetectUTFEncodedFilesCheckBox.isSelected()
       && encodingManager.isNative2AsciiForPropertiesFiles(null) == myTransparentNativeToAsciiCheckBox.isSelected()
      ;
    return !same;
  }

  public boolean isEncodingModified() {
    final Object item = myIdeEncodingsCombo.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(item)) {
      return !StringUtil.isEmpty(EncodingManager.getInstance().getDefaultCharsetName());
    }

    return !Comparing.equal(item, EncodingManager.getInstance().getDefaultCharset());
  }

  public void apply() throws ConfigurationException {
    Map<VirtualFile,Charset> result = myTreeView.getValues();
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    encodingManager.setMapping(result);
    encodingManager.setDefaultCharsetForPropertiesFiles(null, mySelectedCharsetForPropertiesFiles);
    encodingManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());
    encodingManager.setUseUTFGuessing(null, myAutodetectUTFEncodedFilesCheckBox.isSelected());

    final Object item = myIdeEncodingsCombo.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(item)) {
      EncodingManager.getInstance().setDefaultCharsetName("");
    }
    else if (item != null) {
      EncodingManager.getInstance().setDefaultCharsetName(((Charset)item).name());
    }
  }

  public void reset() {
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    myTreeView.reset(encodingManager.getAllMappings());
    myAutodetectUTFEncodedFilesCheckBox.setSelected(encodingManager.isUseUTFGuessing(null));
    myTransparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles(null));
    mySelectedCharsetForPropertiesFiles = encodingManager.getDefaultCharsetForPropertiesFiles(null);
    myAction.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", myAction.getTemplatePresentation(),
                                      ActionManager.getInstance(), 0));

    final DefaultComboBoxModel encodingsModel = new DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets());
    encodingsModel.insertElementAt(SYSTEM_DEFAULT, 0);
    myIdeEncodingsCombo.setModel(encodingsModel);

    final String name = EncodingManager.getInstance().getDefaultCharsetName();
    if (StringUtil.isEmpty(name)) {
      myIdeEncodingsCombo.setSelectedItem(SYSTEM_DEFAULT);
    }
    else {
      myIdeEncodingsCombo.setSelectedItem(EncodingManager.getInstance().getDefaultCharset());
    }
 }

  public void disposeUIResources() {
    myAction = null;
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    myTreeView.select(virtualFile);
  }

  private void createUIComponents() {
    myTreePanel = ScrollPaneFactory.createScrollPane(new JBTable());
  }

  public boolean needDisplay() {
    // TODO[yole] cleaner API
    String platformPrefix = System.getProperty("idea.platform.prefix");
    return !"Ruby".equals(platformPrefix);
  }
}
