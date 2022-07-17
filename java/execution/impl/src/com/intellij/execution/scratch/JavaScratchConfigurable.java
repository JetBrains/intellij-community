// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.scratch;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.*;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.awt.GridBagConstraints.*;

/**
 * @author Eugene Zhuravlev
 */
public class JavaScratchConfigurable extends SettingsEditor<JavaScratchConfiguration> implements PanelWithAnchor {

  private final CommonJavaParametersPanel myCommonProgramParameters;
  private final LabeledComponent<JTextField> myMainClass;
  private final LabeledComponent<TextFieldWithBrowseButton> myScratchPathField;
  private final LabeledComponent<ModuleDescriptionsComboBox> myModule;
  private final JPanel myWholePanel;

  private final ConfigurationModuleSelector myModuleSelector;
  private final LabeledComponent<JBCheckBox> myIncludeProvidedDeps;
  private final JrePathEditor myJrePathEditor;
  private final LabeledComponent<ShortenCommandLineModeCombo> myShortenClasspathModeCombo;
  private JComponent myAnchor;

  public JavaScratchConfigurable(@NotNull  Project project) {
    myMainClass = new LabeledComponent<>();
    myMainClass.setLabelLocation(BorderLayout.WEST);
    myMainClass.setText(ExecutionBundle.message("main.class"));
    myMainClass.setComponent(new JTextField());

    myScratchPathField = new LabeledComponent<>();
    myScratchPathField.setLabelLocation(BorderLayout.WEST);
    myScratchPathField.setText(ExecutionBundle.message("path.to.scratch.file"));
    myScratchPathField.setComponent(new TextFieldWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile toSelect = getVFileFromEditor();
        if (toSelect == null) {
          final String scratchesRoot = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance());
          toSelect = LocalFileSystem.getInstance().findFileByPath(scratchesRoot);
        }
        final VirtualFile file =
          FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), myScratchPathField.getComponent(), project, toSelect);
        if (file != null) {
          setVFileToEditor(file);
        }
      }
    }, this));

    myModule = new LabeledComponent<>();
    myModule.setLabelLocation(BorderLayout.WEST);
    myModule.setComponent(new ModuleDescriptionsComboBox());
    myModule.setText(ExecutionBundle.message("use.classpath.of.module"));
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());

    myCommonProgramParameters = new CommonJavaParametersPanel();
    myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
    myModule.getComponent().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    myJrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(project));

    myIncludeProvidedDeps = new LabeledComponent<>();
    myIncludeProvidedDeps.setLabelLocation(BorderLayout.WEST);
    myIncludeProvidedDeps.setComponent(new JBCheckBox(ExecutionBundle.message("application.configuration.include.provided.scope")));
    myShortenClasspathModeCombo = new LabeledComponent<>();
    myShortenClasspathModeCombo.setLabelLocation(BorderLayout.WEST);
    myShortenClasspathModeCombo.setText(ExecutionBundle.message("application.configuration.shorten.command.line.label"));
    myShortenClasspathModeCombo.setComponent(new ShortenCommandLineModeCombo(project, myJrePathEditor, myModule.getComponent()));

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.add(myMainClass, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));
    myWholePanel.add(myScratchPathField, new GridBagConstraints(RELATIVE, 1, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));
    myWholePanel.add(myCommonProgramParameters, new GridBagConstraints(RELATIVE, 2, 1, 1, 1.0, 1.0, NORTHWEST, BOTH, JBInsets.create(12, 0), 0, 0 ));
    myWholePanel.add(myModule, new GridBagConstraints(RELATIVE, 3, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0 ));
    myWholePanel.add(myIncludeProvidedDeps, new GridBagConstraints(RELATIVE, 4, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));
    myWholePanel.add(myJrePathEditor, new GridBagConstraints(RELATIVE, 5, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(12), 0, 0 ));
    myWholePanel.add(myShortenClasspathModeCombo, new GridBagConstraints(RELATIVE, 6, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));

    myAnchor = UIUtil.mergeComponentsWithAnchor(myMainClass, myScratchPathField, myCommonProgramParameters, myJrePathEditor, myModule,
                                                myShortenClasspathModeCombo, myIncludeProvidedDeps);
  }

  @Override
  public void applyEditorTo(@NotNull JavaScratchConfiguration configuration) throws ConfigurationException {
    myCommonProgramParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);

    configuration.setMainClassName(myMainClass.getComponent().getText().trim());
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setShortenCommandLine(myShortenClasspathModeCombo.getComponent().getSelectedItem());
    configuration.setIncludeProvidedScope(myIncludeProvidedDeps.getComponent().isSelected());

    final VirtualFile vFile = getVFileFromEditor();
    configuration.setScratchFileUrl(vFile != null ? vFile.getUrl() : null);
  }

  @Nullable
  private VirtualFile getVFileFromEditor() {
    final String path = FileUtil.toSystemIndependentName(myScratchPathField.getComponent().getText().trim());
    return !StringUtil.isEmpty(path) ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  @Override
  public void resetEditorFrom(@NotNull JavaScratchConfiguration configuration) {
    myCommonProgramParameters.reset(configuration);
    myModuleSelector.reset(configuration);

    myMainClass.getComponent().setText(configuration.getMainClassName() != null ? configuration.getMainClassName().replaceAll("\\$", "\\.") : "");
    myJrePathEditor.setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
    myShortenClasspathModeCombo.getComponent().setSelectedItem(configuration.getShortenCommandLine());
    myIncludeProvidedDeps.getComponent().setSelected(configuration.isProvidedScopeIncluded());
    setVFileToEditor(configuration.getScratchVirtualFile());
  }

  private void setVFileToEditor(VirtualFile file) {
    myScratchPathField.getComponent().setText(file != null? FileUtil.toSystemDependentName(file.getPath()): "");
  }

  @NotNull
  @Override
  public JComponent createEditor() {
    return myWholePanel;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
    myMainClass.setAnchor(anchor);
    myScratchPathField.setAnchor(anchor);
    myCommonProgramParameters.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
    myModule.setAnchor(anchor);
    myShortenClasspathModeCombo.setAnchor(anchor);
  }
}
