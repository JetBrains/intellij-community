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

package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
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
import java.util.HashMap;
import java.util.Map;

public class FileEncodingConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private EncodingFileTreeTable myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private final Ref<Charset> mySelectedCharsetForPropertiesFiles = new Ref<>();
  private final Ref<Charset> mySelectedIdeCharset = new Ref<>();           // IDE encoding or null if "System Default"
  private final Ref<Charset> mySelectedProjectCharset = new Ref<>(); // Project encoding or null if "System Default"
  private JLabel myTitleLabel;
  private JPanel myIdeEncodingsListCombo;
  private JPanel myProjectEncodingListCombo;
  private ChooseFileEncodingAction myPropertiesEncodingAction;
  private ChooseFileEncodingAction myIdeEncodingAction;
  private ChooseFileEncodingAction myProjectEncodingAction;

  public FileEncodingConfigurable(@NotNull Project project) {
    myProject = project;
    myTitleLabel.setText(myTitleLabel.getText().replace("$productName", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Override
  @Nls
  public String getDisplayName() {
    return IdeBundle.message("file.encodings.configurable");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.file.encodings";
  }

  @Override
  @NotNull
  public String getId() {
    return "File.Encoding";
  }

  @NotNull
  private static ChooseFileEncodingAction installChooseEncodingCombo(@NotNull JPanel parentPanel, @NotNull final Ref<Charset> selected) {
    ChooseFileEncodingAction myAction = new ChooseFileEncodingAction(null) {
      @Override
      public void update(final AnActionEvent e) {
        getTemplatePresentation().setEnabled(true);
        Charset charset = selected.get();
        getTemplatePresentation().setText(charset == null ? IdeBundle.message("encoding.name.system.default", CharsetToolkit.getDefaultSystemCharset().displayName()) : charset.displayName());
      }

      @Override
      protected void chosen(final VirtualFile virtualFile, @NotNull final Charset charset) {
        selected.set(charset == NO_ENCODING ? null : charset);
        update(null);
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        return createCharsetsActionGroup("<System Default>", selected.get(), charset -> "Choose encoding '" + charset + "'");
      }
    };
    parentPanel.removeAll();
    Presentation templatePresentation = myAction.getTemplatePresentation();
    parentPanel.add(myAction.createCustomComponent(templatePresentation), BorderLayout.CENTER);
    myAction.update(null);
    return myAction;
  }

  @Override
  public JComponent createComponent() {
    myPropertiesEncodingAction = installChooseEncodingCombo(myPropertiesFilesEncodingCombo, mySelectedCharsetForPropertiesFiles);
    myIdeEncodingAction = installChooseEncodingCombo(myIdeEncodingsListCombo, mySelectedIdeCharset);
    myProjectEncodingAction = installChooseEncodingCombo(myProjectEncodingListCombo, mySelectedProjectCharset);
    myTreeView = new EncodingFileTreeTable(myProject);
    myTreePanel.setViewportView(myTreeView);
    myTreeView.getEmptyText().setText(IdeBundle.message("file.encodings.not.configured"));
    return myPanel;
  }

  @Override
  public boolean isModified() {
    if (isIdeEncodingModified()) return true;
    if (isProjectEncodingModified()) return true;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);

    Map<VirtualFile, Charset> editing = myTreeView.getValues();
    Map<VirtualFile, Charset> existingMapping = getExistingMappingIncludingDefault(myProject);
    boolean same = editing.equals(existingMapping)
       && Comparing.equal(encodingManager.getDefaultCharsetForPropertiesFiles(null), mySelectedCharsetForPropertiesFiles.get())
       && encodingManager.isNative2AsciiForPropertiesFiles() == myTransparentNativeToAsciiCheckBox.isSelected()
      ;
    return !same;
  }

  @NotNull
  static Map<VirtualFile, Charset> getExistingMappingIncludingDefault(@NotNull Project project) {
    Map<VirtualFile, Charset> existingMapping = new HashMap<>();
    EncodingProjectManagerImpl encodingProjectManager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(project);
    existingMapping.putAll(encodingProjectManager.getAllMappings());
    existingMapping.put(null, encodingProjectManager.getDefaultCharset());
    return existingMapping;
  }

  private boolean isIdeEncodingModified() {
    String charsetName = getSelectedCharsetName(mySelectedIdeCharset);
    return !charsetName.equals(EncodingManager.getInstance().getDefaultCharsetName());
  }
  private boolean isProjectEncodingModified() {
    String charsetName = getSelectedCharsetName(mySelectedProjectCharset);
    return !charsetName.equals(EncodingProjectManager.getInstance(myProject).getDefaultCharsetName());
  }

  @NotNull // charset name or empty for System Default
  private static String getSelectedCharsetName(@NotNull Ref<Charset> selectedCharset) {
    Charset charset = selectedCharset.get();
    return charset == null ? "" : charset.name();
  }

  @Override
  public void apply() throws ConfigurationException {
    String projectCharsetName = getSelectedCharsetName(mySelectedProjectCharset);

    Map<VirtualFile,Charset> result = myTreeView.getValues();
    EncodingProjectManagerImpl encodingProjectManager = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(myProject));
    encodingProjectManager.setMapping(result);
    encodingProjectManager.setDefaultCharsetName(projectCharsetName);
    encodingProjectManager.setDefaultCharsetForPropertiesFiles(null, mySelectedCharsetForPropertiesFiles.get());
    encodingProjectManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());

    Charset ideCharset = mySelectedIdeCharset.get();
    EncodingManager.getInstance().setDefaultCharsetName(ideCharset == null ? "" : ideCharset.name());
  }

  @Override
  public void reset() {
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    myTreeView.reset(getExistingMappingIncludingDefault(myProject));
    myTransparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles());
    mySelectedCharsetForPropertiesFiles.set(encodingManager.getDefaultCharsetForPropertiesFiles(null));

    mySelectedIdeCharset.set(EncodingManager.getInstance().getDefaultCharsetName().isEmpty() ? null : EncodingManager.getInstance().getDefaultCharset());
    mySelectedProjectCharset.set(encodingManager.getDefaultCharsetName().isEmpty() ? null : encodingManager.getDefaultCharset());
    myPropertiesEncodingAction.update(null);
    myIdeEncodingAction.update(null);
    myProjectEncodingAction.update(null);
 }

  @Override
  public void disposeUIResources() {
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    myTreeView.select(virtualFile);
  }

  private void createUIComponents() {
    myTreePanel = ScrollPaneFactory.createScrollPane(new JBTable());
  }
}
