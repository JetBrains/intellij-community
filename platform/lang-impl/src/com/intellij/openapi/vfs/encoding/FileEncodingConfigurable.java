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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.Map;

public class FileEncodingConfigurable implements SearchableConfigurable, OptionalConfigurable, Configurable.NoScroll {
  private static final String SYSTEM_DEFAULT = IdeBundle.message("encoding.name.system.default");
  private final Project myProject;
  private EncodingFileTreeTable myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JCheckBox myAutodetectUTFEncodedFilesCheckBox;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private final Ref<Charset> mySelectedCharsetForPropertiesFiles = new Ref<Charset>();
  private final Ref<Charset> mySelectedIdeCharset = new Ref<Charset>();
  private final Ref<Charset> mySelectedProjectCharset = new Ref<Charset>();
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

  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  private static ChooseFileEncodingAction installChooseEncodingCombo(@NotNull JPanel parentPanel, @NotNull final Ref<Charset> selected) {
    ChooseFileEncodingAction myAction = new ChooseFileEncodingAction(null) {
      @Override
      public void update(final AnActionEvent e) {
        getTemplatePresentation().setEnabled(true);
        Charset charset = selected.get();
        getTemplatePresentation().setText(charset == null ? SYSTEM_DEFAULT : charset.displayName());
      }

      @Override
      protected void chosen(final VirtualFile virtualFile, @NotNull final Charset charset) {
        selected.set(charset == NO_ENCODING ? null : charset);
        update(null);
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        return createGroup("<System Default>", "Choose encoding ''{1}''", selected.get(), null);
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
    Map<VirtualFile, Charset> mapping = EncodingProjectManager.getInstance(myProject).getAllMappings();
    boolean same = editing.equals(mapping)
       && Comparing.equal(encodingManager.getDefaultCharsetForPropertiesFiles(null), mySelectedCharsetForPropertiesFiles.get())
       && encodingManager.isUseUTFGuessing(null) == myAutodetectUTFEncodedFilesCheckBox.isSelected()
       && encodingManager.isNative2AsciiForPropertiesFiles() == myTransparentNativeToAsciiCheckBox.isSelected()
      ;
    return !same;
  }

  private boolean isIdeEncodingModified() {
    Charset charset = mySelectedIdeCharset.get();
    if (null == charset) {
      return !StringUtil.isEmpty(EncodingManager.getInstance().getDefaultCharsetName());
    }

    return !Comparing.equal(charset, EncodingManager.getInstance().getDefaultCharset());
  }
  private boolean isProjectEncodingModified() {
    Charset charset = mySelectedProjectCharset.get();
    return !Comparing.equal(charset, EncodingProjectManager.getInstance(myProject).getEncoding(null, false));
  }

  @Override
  public void apply() throws ConfigurationException {
    Map<VirtualFile,Charset> result = myTreeView.getValues();
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    encodingManager.setMapping(result);
    encodingManager.setDefaultCharsetForPropertiesFiles(null, mySelectedCharsetForPropertiesFiles.get());
    encodingManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());
    encodingManager.setUseUTFGuessing(null, myAutodetectUTFEncodedFilesCheckBox.isSelected());

    Charset ideCharset = mySelectedIdeCharset.get();
    EncodingManager.getInstance().setDefaultCharsetName(ideCharset == null ? "" : ideCharset.name());
    Charset projectCharset = mySelectedProjectCharset.get();
    EncodingProjectManager.getInstance(myProject).setEncoding(null, projectCharset);
  }

  @Override
  public void reset() {
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    myTreeView.reset(encodingManager.getAllMappings());
    myAutodetectUTFEncodedFilesCheckBox.setSelected(encodingManager.isUseUTFGuessing(null));
    myTransparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles());
    mySelectedCharsetForPropertiesFiles.set(encodingManager.getDefaultCharsetForPropertiesFiles(null));

    mySelectedIdeCharset.set(EncodingManager.getInstance().getDefaultCharset());
    mySelectedProjectCharset.set(EncodingProjectManager.getInstance(myProject).getEncoding(null, false));
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

  @Override
  public boolean needDisplay() {
    // TODO[yole] cleaner API
    return !PlatformUtils.isRubyMine();
  }
}
