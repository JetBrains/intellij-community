// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class SourcePathsStep extends AbstractStepWithProgress<List<JavaModuleSourceRoot>> {

  private static final Logger LOG = Logger.getInstance(SourcePathsStep.class);

  private String myCurrentMode;
  @NonNls private static final String CREATE_SOURCE_PANEL = "create_source";
  @NonNls private static final String CHOOSE_SOURCE_PANEL = "choose_source";

  private final SourcePathsBuilder myBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private ElementsChooser<JavaModuleSourceRoot> mySourcePathsChooser;
  private String myCurrentContentEntryPath;
  private JRadioButton myRbCreateSource;
  private JRadioButton myRbNoSource;
  private JTextField myTfSourceDirectoryName;
  private JTextField myTfFullPath;
  private JPanel myResultPanel;

  public SourcePathsStep(SourcePathsBuilder builder, Icon icon, @NonNls String helpId) {
    super(JavaUiBundle.message("prompt.stop.searching.for.sources", ApplicationNamesInfo.getInstance().getProductName()));
    myBuilder = builder;
    myIcon = icon;
    myHelpId = helpId;
  }

  @Override
  protected JComponent createResultsPanel() {
    myResultPanel = new JPanel(new CardLayout());
    myResultPanel.add(createComponentForEmptyRootCase(), CREATE_SOURCE_PANEL);
    myResultPanel.add(createComponentForChooseSources(), CHOOSE_SOURCE_PANEL);
    return myResultPanel;
  }

  private JComponent createComponentForEmptyRootCase() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final String text = JavaUiBundle.message("prompt.please.specify.java.sources.directory");

    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                            JBUI.insets(8, 10, 0, 10), 0, 0));

    myRbCreateSource = new JRadioButton(JavaUiBundle.message("radio.create.source.directory"), true);
    panel.add(myRbCreateSource, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                       JBUI.insets(8, 10, 0, 10), 0, 0));

    myTfSourceDirectoryName = new JTextField(suggestSourceDirectoryName());
    final JLabel srcPathLabel = new JLabel(JavaUiBundle.message("prompt.enter.relative.path.to.module.content.root", File.separator));
    panel.add(srcPathLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                   JBUI.insets(8, 30, 0, 0), 0, 0));
    final FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    chooserDescriptor.withTreeRootVisible(true);
    final FieldPanel fieldPanel = createFieldPanel(myTfSourceDirectoryName, null, new BrowsePathListener(myTfSourceDirectoryName, chooserDescriptor));
    panel.add(fieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                 JBUI.insets(8, 30, 0, 10), 0, 0));

    myRbNoSource = new JRadioButton(JavaUiBundle.message("radio.do.not.create.source.directory"), true);
    panel.add(myRbNoSource, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                   JBUI.insets(8, 10, 0, 10), 0, 0));

    final JLabel fullPathLabel = new JLabel(JavaUiBundle.message("label.source.directory"));
    panel.add(fullPathLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE,
                                                    JBUI.insets(8, 10, 0, 10), 0, 0));

    myTfFullPath = new JTextField();
    myTfFullPath.setEditable(false);
    myTfFullPath.setOpaque(false);
    final Insets borderInsets = myTfFullPath.getBorder().getBorderInsets(myTfFullPath);
    myTfFullPath.setBorder(BorderFactory.createEmptyBorder(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right));
    panel.add(myTfFullPath, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL,
                                                   JBInsets.create(8, 10), 0, 0));

    ButtonGroup group = new ButtonGroup();
    group.add(myRbCreateSource);
    group.add(myRbNoSource);
    myTfSourceDirectoryName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        updateFullPathField();
      }
    });

    myRbCreateSource.addItemListener(e -> {
      final boolean enabled = e.getStateChange() == ItemEvent.SELECTED;
      srcPathLabel.setEnabled(enabled);
      fieldPanel.setEnabled(enabled);
      fullPathLabel.setVisible(enabled);
      myTfFullPath.setVisible(enabled);
      if (enabled) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTfSourceDirectoryName, true));
      }
    });
    return panel;
  }

  protected @NlsSafe String suggestSourceDirectoryName() {
    return "src";
  }

  private void updateFullPathField() {
    final String sourceDirectoryPath = getSourceDirectoryPath();
    if (sourceDirectoryPath != null) {
      myTfFullPath.setText(sourceDirectoryPath.replace('/', File.separatorChar));
    }
    else {
      myTfFullPath.setText("");
    }
  }

  private JComponent createComponentForChooseSources() {
    final JPanel panel = new JPanel(new GridBagLayout());
    mySourcePathsChooser = new ElementsChooser<>(true) {
      @Override
      public String getItemText(@NotNull JavaModuleSourceRoot sourceRoot) {
        String packagePrefix = sourceRoot.getPackagePrefix();
        return sourceRoot.getDirectory().getAbsolutePath() +
               (packagePrefix.isEmpty() ? "" : " (" + packagePrefix + ")") +
               " [" + sourceRoot.getRootTypeName() + "]";
      }
    };
    final String text = JavaUiBundle.message("label.java.source.files.have.been.found");
    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                            JBUI.insets(8, 10, 0, 10), 0, 0));
    panel.add(mySourcePathsChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                           JBInsets.create(8, 10), 0, 0));

    final JButton markAllButton = new JButton(JavaUiBundle.message("button.mark.all"));
    panel.add(markAllButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    JBUI.insets(0, 10, 8, 2), 0, 0));

    final JButton unmarkAllButton = new JButton(JavaUiBundle.message("button.unmark.all"));
    panel.add(unmarkAllButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                      JBUI.insets(0, 0, 8, 10), 0, 0));

    markAllButton.addActionListener(__ -> mySourcePathsChooser.setAllElementsMarked(true));
    unmarkAllButton.addActionListener(__ -> mySourcePathsChooser.setAllElementsMarked(false));

    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRbCreateSource.isSelected()? myTfSourceDirectoryName : mySourcePathsChooser.getComponent();
  }

  @Override
  public void updateDataModel() {
    List<Pair<String,String>> paths = null;
    if (CHOOSE_SOURCE_PANEL.equals(myCurrentMode)) {
      final List<JavaModuleSourceRoot> selectedElements = mySourcePathsChooser.getMarkedElements();
      if (!selectedElements.isEmpty()) {
        paths = new ArrayList<>(selectedElements.size());

        for (final JavaModuleSourceRoot root : selectedElements) {
          paths.add(Pair.create(FileUtil.toSystemIndependentName(root.getDirectory().getAbsolutePath()), root.getPackagePrefix()));
        }
      }
    }
    else {
      if (myRbCreateSource.isSelected()) {
        final String sourceDirectoryPath = getSourceDirectoryPath();
        if (sourceDirectoryPath != null) {
          paths = Collections.singletonList(Pair.create(sourceDirectoryPath, ""));
        }
      }
    }
    myBuilder.setContentEntryPath(getContentRootPath());
    if (paths != null) {
      myBuilder.setSourcePaths(paths);
    }
    else {
      myBuilder.setSourcePaths(new ArrayList<>());
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (!super.validate()) {
      return false;
    }

    if (CREATE_SOURCE_PANEL.equals(myCurrentMode) && myRbCreateSource.isSelected()) {
      final String sourceDirectoryPath = getSourceDirectoryPath();
      final String relativePath = myTfSourceDirectoryName.getText().trim();
      if (sourceDirectoryPath != null) {
        if (relativePath.isEmpty()) {
          String text = JavaUiBundle.message("prompt.relative.path.to.sources.empty", FileUtil.toSystemDependentName(sourceDirectoryPath));
          final int answer = Messages.showYesNoCancelDialog(myTfSourceDirectoryName, text, JavaUiBundle.message("title.mark.source.directory"),
                                                            JavaUiBundle.message("action.mark"), JavaUiBundle.message("action.do.not.mark"),
                                                            CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
          if (answer == Messages.CANCEL) {
            return false; // cancel
          }
          if (answer == Messages.NO) { // don't mark
            myRbNoSource.doClick();
          }
        }
        final String contentRootPath = getContentRootPath();
        if (contentRootPath != null) {
          final File rootDir = new File(contentRootPath);
          final File srcDir = new File(sourceDirectoryPath);
          if (!FileUtil.isAncestor(rootDir, srcDir, false)) {
            Messages.showErrorDialog(myTfSourceDirectoryName,
                                     JavaUiBundle.message("error.source.directory.should.be.under.module.content.root.directory"),
                                     CommonBundle.getErrorTitle());
            return false;
          }
          try {
            VfsUtil.createDirectories(srcDir.getPath());
          }
          catch (IOException e) {
            throw new ConfigurationException(e.getMessage());
          }
        }
      }
    }
    return true;
  }

  @Nullable
  private String getSourceDirectoryPath() {
    final String contentEntryPath = getContentRootPath();
    if (contentEntryPath != null) {
      final String dirName = myTfSourceDirectoryName.getText().trim().replace(File.separatorChar, '/');
      return !dirName.isEmpty() ? contentEntryPath + "/" + dirName : contentEntryPath;
    }
    return null;
  }

  @Override
  protected boolean shouldRunProgress() {
    return isContentEntryChanged();
  }

  @Override
  protected void onFinished(final List<JavaModuleSourceRoot> foundPaths, final boolean canceled) {
    List<JavaModuleSourceRoot> paths = ContainerUtil.notNullize(foundPaths);
    if (!paths.isEmpty()) {
      myCurrentMode = CHOOSE_SOURCE_PANEL;
      mySourcePathsChooser.setElements(paths, true);
    }
    else {
      myCurrentMode = CREATE_SOURCE_PANEL;
      updateFullPathField();
    }
    updateStepUI(canceled ? null : getContentRootPath());
    if (CHOOSE_SOURCE_PANEL.equals(myCurrentMode)) {
      mySourcePathsChooser.selectElements(paths.subList(0, 1));
    }
    else if (CREATE_SOURCE_PANEL.equals(myCurrentMode)) {
      myTfSourceDirectoryName.selectAll();
    }
  }

  private void updateStepUI(final String contentEntryPath) {
    myCurrentContentEntryPath = contentEntryPath;
    ((CardLayout)myResultPanel.getLayout()).show(myResultPanel, myCurrentMode);
    myResultPanel.revalidate();
  }

  private boolean isContentEntryChanged() {
    final String contentEntryPath = getContentRootPath();
    return !Objects.equals(myCurrentContentEntryPath, contentEntryPath);
  }

  @Override
  protected List<JavaModuleSourceRoot> calculate() {
    return new ArrayList<>(calculateSourceRoots(getContentRootPath()));
  }

  @NotNull
  public static Collection<JavaModuleSourceRoot> calculateSourceRoots(final String contentRootPath) {
    if (contentRootPath == null) {
      return Collections.emptyList();
    }
    return JavaSourceRootDetectionUtil.suggestRoots(new File(contentRootPath));
  }

  @Nullable
  private String getContentRootPath() {
    return myBuilder.getContentEntryPath();
  }

  @Override
  protected String getProgressText() {
    final String root = getContentRootPath();
    return JavaUiBundle.message("progress.searching.for.sources", root != null? root.replace('/', File.separatorChar) : "") ;
  }

  public static FieldPanel createFieldPanel(final JTextField field, final @NlsContexts.Label String labelText, final BrowseFilesListener browseButtonActionListener) {
    final FieldPanel fieldPanel = new FieldPanel(field, labelText, null, browseButtonActionListener, null);
    fieldPanel.getFieldLabel().setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
    return fieldPanel;
  }

  private class BrowsePathListener extends BrowseFilesListener {
    private final JTextField myField;

    BrowsePathListener(JTextField textField, final FileChooserDescriptor chooserDescriptor) {
      super(textField, JavaUiBundle.message("prompt.select.source.directory"), "", chooserDescriptor);
      myField = textField;
    }

    @Nullable
    private VirtualFile getContentEntryDir() {
      final String contentEntryPath = getContentRootPath();
      if (contentEntryPath != null) {
        return WriteAction
          .compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath));
      }
      return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final VirtualFile contentEntryDir = getContentEntryDir();
      if (contentEntryDir != null) {
        myChooserDescriptor.setRoots(contentEntryDir);
        final String textBefore = myField.getText().trim();
        super.actionPerformed(e);
        if (!textBefore.equals(myField.getText().trim())) {
          final String fullPath = myField.getText().trim().replace(File.separatorChar, '/');
          final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(fullPath);
          LOG.assertTrue(fileByPath != null);
          myField.setText(VfsUtilCore.getRelativePath(fileByPath, contentEntryDir, File.separatorChar));
        }
      }
    }
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public String getHelpId() {
    return myHelpId;
  }

  @Override
  public String getName() {
    return "Path to Sources";
  }
}
