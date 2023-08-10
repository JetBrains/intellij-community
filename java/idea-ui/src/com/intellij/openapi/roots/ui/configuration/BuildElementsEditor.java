// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

public class BuildElementsEditor extends ModuleElementsEditor {
  private JRadioButton myInheritCompilerOutput;
  private JRadioButton myPerModuleCompilerOutput;

  private CommittableFieldPanel myOutputPathPanel;
  private CommittableFieldPanel myTestsOutputPathPanel;
  private JCheckBox myCbExcludeOutput;
  private JLabel myOutputLabel;
  private JLabel myTestOutputLabel;

  protected BuildElementsEditor(final ModuleConfigurationState state) {
    super(state);
  }

  @Override
  public JComponent createComponentImpl() {
    myInheritCompilerOutput = new JRadioButton(JavaUiBundle.message("project.inherit.compile.output.path"));
    myPerModuleCompilerOutput = new JRadioButton(JavaUiBundle.message("project.module.compile.output.path"));
    ButtonGroup group = new ButtonGroup();
    group.add(myInheritCompilerOutput);
    group.add(myPerModuleCompilerOutput);

    myOutputPathPanel = createOutputPathPanel(JavaUiBundle.message("module.paths.output.title"), new CommitPathRunnable() {
      @Override
      public void saveUrl(String url) {
        if (myInheritCompilerOutput.isSelected()) return;  //do not override settings if any
        getCompilerExtension().setCompilerOutputPath(url);
        fireModuleConfigurationChanged();
      }
    });
    myTestsOutputPathPanel = createOutputPathPanel(JavaUiBundle.message("module.paths.test.output.title"), new CommitPathRunnable() {
      @Override
      public void saveUrl(String url) {
        if (myInheritCompilerOutput.isSelected()) return; //do not override settings if any
        getCompilerExtension().setCompilerOutputPathForTests(url);
        fireModuleConfigurationChanged();
      }
    });

    myCbExcludeOutput = new JCheckBox(JavaUiBundle.message("module.paths.exclude.output.checkbox"), getCompilerExtension().isExcludeOutput());
    myCbExcludeOutput.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        getCompilerExtension().setExcludeOutput(myCbExcludeOutput.isSelected());
        fireModuleConfigurationChanged();
      }
    });

    final JPanel outputPathsPanel = new JPanel(new GridBagLayout());


    outputPathsPanel.add(myInheritCompilerOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0,
                                                                         GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                                         JBUI.insets(6, 0, 0, 4), 0, 0));
    outputPathsPanel.add(myPerModuleCompilerOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0,
                                                                           GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                                           JBUI.insets(6, 0, 0, 4), 0, 0));

    myOutputLabel = new JLabel(JavaUiBundle.message("module.paths.output.label"));
    outputPathsPanel.add(myOutputLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                                                               GridBagConstraints.NONE, JBUI.insets(6, 12, 0, 4), 0, 0));
    myOutputLabel.setLabelFor(myOutputPathPanel.getTextField());
    outputPathsPanel.add(myOutputPathPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                                                   GridBagConstraints.HORIZONTAL, JBUI.insets(6, 4, 0, 0), 0, 0));

    myTestOutputLabel = new JLabel(JavaUiBundle.message("module.paths.test.output.label"));
    outputPathsPanel.add(myTestOutputLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                                                                   GridBagConstraints.NONE, JBUI.insets(6, 16, 0, 4), 0, 0));
    myTestOutputLabel.setLabelFor(myTestsOutputPathPanel.getTextField());
    outputPathsPanel.add(myTestsOutputPathPanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0,
                                                                        GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                                        JBUI.insets(6, 4, 0, 0), 0, 0));

    outputPathsPanel.add(myCbExcludeOutput, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.WEST,
                                                                   GridBagConstraints.NONE, JBUI.insets(6, 16, 0, 0), 0, 0));

    final boolean outputPathInherited = getCompilerExtension().isCompilerOutputPathInherited();
    myInheritCompilerOutput.setSelected(outputPathInherited);
    myPerModuleCompilerOutput.setSelected(!outputPathInherited);

    // fill with data
    updateOutputPathPresentation();

    //compiler settings
    enableCompilerSettings(!outputPathInherited);

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        enableCompilerSettings(!myInheritCompilerOutput.isSelected());
      }
    };

    myInheritCompilerOutput.addActionListener(listener);
    myPerModuleCompilerOutput.addActionListener(listener);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(JavaUiBundle.message("project.roots.output.compiler.title")));
    panel.add(outputPathsPanel, BorderLayout.NORTH);
    return panel;
  }

  private void fireModuleConfigurationChanged() {
    fireConfigurationChanged();
  }

  private void updateOutputPathPresentation() {
    if (getCompilerExtension().isCompilerOutputPathInherited()) {
      ProjectConfigurable projectConfig = ((ModulesConfigurator)getState().getModulesProvider()).getProjectStructureConfigurable().getProjectConfig();
      if (projectConfig == null) {
        return;
      }
      final String baseUrl = projectConfig.getCompilerOutputUrl();
      moduleCompileOutputChanged(baseUrl, getModel().getModule().getName());
    } else {
      final VirtualFile compilerOutputPath = getCompilerExtension().getCompilerOutputPath();
      if (compilerOutputPath != null) {
        myOutputPathPanel.setText(FileUtil.toSystemDependentName(compilerOutputPath.getPath()));
      }
      else {
        final String compilerOutputUrl = getCompilerExtension().getCompilerOutputUrl();
        if (compilerOutputUrl != null) {
          myOutputPathPanel.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(compilerOutputUrl)));
        }
      }
      final VirtualFile testsOutputPath = getCompilerExtension().getCompilerOutputPathForTests();
      if (testsOutputPath != null) {
        myTestsOutputPathPanel.setText(FileUtil.toSystemDependentName(testsOutputPath.getPath()));
      }
      else {
        final String testsOutputUrl = getCompilerExtension().getCompilerOutputUrlForTests();
        if (testsOutputUrl != null) {
          myTestsOutputPathPanel.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(testsOutputUrl)));
        }
      }
    }
  }

  private void enableCompilerSettings(final boolean enabled) {
    UIUtil.setEnabled(myOutputPathPanel, enabled, true);
    UIUtil.setEnabled(myOutputLabel, enabled, true);
    UIUtil.setEnabled(myTestsOutputPathPanel, enabled, true);
    UIUtil.setEnabled(myTestOutputLabel, enabled, true);
    myCbExcludeOutput.setEnabled(enabled);
    getCompilerExtension().inheritCompilerOutputPath(!enabled);
    updateOutputPathPresentation();
    fireModuleConfigurationChanged();
  }

  private CommittableFieldPanel createOutputPathPanel(final @NlsContexts.DialogTitle String title, final CommitPathRunnable commitPathRunnable) {
    final JTextField textField = new ExtendableTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    outputPathsChooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, getModel().getModule());
    outputPathsChooserDescriptor.setHideIgnored(false);
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    FileChooserFactory.getInstance().installFileCompletion(textField, outputPathsChooserDescriptor, true, null);
    final Runnable commitRunnable = () -> {
      if (!getModel().isWritable()) {
        return;
      }
      final String path = textField.getText().trim();
      if (path.length() == 0) {
        commitPathRunnable.saveUrl(null);
      }
      else {
        // should set only absolute paths
        String canonicalPath;
        try {
          canonicalPath = FileUtil.resolveShortWindowsName(path);
        }
        catch (IOException e) {
          canonicalPath = path;
        }
        commitPathRunnable.saveUrl(VfsUtilCore.pathToUrl(canonicalPath));
      }
    };

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        commitRunnable.run();
      }
    };
    myPerModuleCompilerOutput.addActionListener(listener);

    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        commitRunnable.run();
      }
    });

    return new CommittableFieldPanel(textField, new BrowseFilesListener(textField, title, "", outputPathsChooserDescriptor) {
      @Override
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        commitRunnable.run();
      }
    }, null, commitRunnable);
  }

  @Override
  public void saveData() {
    myOutputPathPanel.commit();
    myTestsOutputPathPanel.commit();
  }

  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("output.tab.title");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.structureModulesPage.outputJavadoc";
  }


  @Override
  public void moduleStateChanged() {
    //if content entries tree was changed
    myCbExcludeOutput.setSelected(getCompilerExtension().isExcludeOutput());
  }

  @Override
  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {
    if (getCompilerExtension().isCompilerOutputPathInherited()) {
      if (baseUrl != null) {
        myOutputPathPanel.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(baseUrl + "/" + CompilerModuleExtension
          .PRODUCTION + "/" + moduleName)));
        myTestsOutputPathPanel.setText(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(baseUrl + "/" + CompilerModuleExtension
          .TEST + "/" + moduleName)));
      }
      else {
        myOutputPathPanel.setText(null);
        myTestsOutputPathPanel.setText(null);
      }
    }
  }

  public CompilerModuleExtension getCompilerExtension() {
    return getModel().getModuleExtension(CompilerModuleExtension.class);
  }

  private interface CommitPathRunnable {
    void saveUrl(String url);
  }

  private static class CommittableFieldPanel extends FieldPanel {
    private final Runnable myCommitRunnable;

    CommittableFieldPanel(final JTextField textField,
                          ActionListener browseButtonActionListener,
                          final Runnable documentListener,
                          final Runnable commitPathRunnable) {
      super(textField, null, null, browseButtonActionListener, documentListener);
      myCommitRunnable = commitPathRunnable;
    }

    public void commit() {
      myCommitRunnable.run();
    }
  }
}